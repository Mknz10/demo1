package com.example.memgraph_api.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatClient chatClient;
    private final Driver driver;
    private final EmbeddingModel embeddingModel;

    @Autowired
    public ChatController(ChatClient.Builder chatClientBuilder, Driver driver, EmbeddingModel embeddingModel) {
        this.chatClient = chatClientBuilder.build();
        this.driver = driver;
        this.embeddingModel = embeddingModel;
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> chat(@RequestBody Map<String, String> payload) {
        String message = payload.getOrDefault("message", "").trim();

        if (message.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Message is empty."));
        }

        try {
            // =====================================================
            // 1. AUTH CONTEXT (ONLY SOURCE OF TRUTH)
            // =====================================================
            String identifier = payload.get("identifier");
            
            if (identifier == null || identifier.isBlank()) {
                return ResponseEntity.ok(Map.of(
                        "reply_html", "<div>Missing user identifier. Please log in.</div>"
                ));
            }

            String role = getUserRole(identifier);
            if (role == null) {
                return ResponseEntity.ok(Map.of(
                        "reply_html", "<div>User not found or unauthenticated.</div>"
                ));
            }

            boolean isPractitioner = "Practitioner".equals(role);
            String baseMatch = isPractitioner 
                ? "MATCH (:Practitioner {identifier: '" + identifier + "'})-[:TREATS]->(p:Patient)" 
                : "MATCH (p:Patient {identifier: '" + identifier + "'})";

            // =====================================================
            // 2. PREGĂTIRE CONTEXT PENTRU RAG
            // =====================================================
            String msgLower = message.toLowerCase();
            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> columns = new ArrayList<>();
            StringBuilder contextBuilder = new StringBuilder();
            String tableTitle = "Căutare Vectorială Semantică";
            boolean skipSemantic = false;
            
            if (msgLower.contains("document") || msgLower.contains("file") || msgLower.contains("fișier") || msgLower.contains("pdf")) {
                
                String fileName = null;
                // Extragem numele imediat după cuvântul "document", omițând semnele de punctuație
                java.util.regex.Matcher mName = java.util.regex.Pattern.compile("(?i)(?:file|documentul?|fișierul?|pdf[-ul ]*)\\s+([^?.,!]+)").matcher(message);
                if (mName.find()) {
                    fileName = mName.group(1).trim();
                    // Ignorăm cuvintele generice care nu reprezintă fișiere efective
                    if (fileName.equalsIgnoreCase("meu") || fileName.equalsIgnoreCase("medical") || fileName.equalsIgnoreCase("mele") || fileName.length() < 3) {
                        fileName = null;
                    }
                }

                if (fileName != null) {
                    // Căutare explicită a conținutului unui document pentru a-l oferi AI-ului
                    String cypher = isPractitioner ?
                        "MATCH (:Practitioner {identifier: $id})-[:TREATS]->(p:Patient)<-[:subject]-(n)-[:source]->(d:Document) " :
                        "MATCH (p:Patient {identifier: $id})<-[:subject]-(n)-[:source]->(d:Document) ";
                    cypher += "WHERE toLower(d.name) = toLower($fileName) OR toLower(d.name) = toLower($fileName) + '.pdf' " +
                              "RETURN d.name AS Document, labels(n)[0] AS Type, n.display AS Display, n.valueQuantity_value AS Value, n.valueQuantity_unit AS Unit, n.dosageInstruction_text AS Dose " +
                              "LIMIT 200";

                    try (Session session = driver.session()) {
                        Result result = session.run(cypher, Map.of("id", identifier, "fileName", fileName));
                        columns = result.keys();
                        while (result.hasNext()) {
                            Map<String, Object> row = result.next().asMap();
                            rows.add(row);
                            contextBuilder.append(row.toString()).append("\n");
                        }
                    } catch (Exception e) {
                        return ResponseEntity.ok(Map.of("reply_html", "<div>Eroare la extragerea documentului: " + e.getMessage() + "</div>"));
                    }

                    if (!rows.isEmpty()) {
                        skipSemantic = true; // Sărim peste căutarea generală, ne concentrăm pe document!
                        tableTitle = "Extragere din: " + fileName;
                    }
                }
                
                // Dacă nu s-a găsit un fișier specific dar utilizatorul vrea de fapt doar o listă
                if (!skipSemantic && (msgLower.contains("lista") || msgLower.contains("ce documente") || msgLower.contains("arată") || msgLower.contains("toate") || msgLower.contains("documente"))) {
                    String cypher = isPractitioner ?
                        "MATCH (:Practitioner {identifier: $id})-[:TREATS]->(p:Patient)<-[:subject]-(n)-[:source]->(d:Document) " +
                        "WHERE d.name IS NOT NULL " +
                        "RETURN DISTINCT d.name AS Nume, p.identifier AS Pacient, d.uploadDate AS `Data încărcării`, d.releaseDate AS `Data eliberării`, CASE WHEN toLower(d.name) CONTAINS 'upu' THEN 'Fișă UPU' WHEN toLower(d.name) CONTAINS 'analize' THEN 'Analize Laborator' ELSE 'Document Medical' END AS Proprietăți " +
                        "ORDER BY `Data încărcării` DESC LIMIT 20" :
                        "MATCH (p:Patient {identifier: $id})<-[:subject]-(n)-[:source]->(d:Document) " +
                        "WHERE d.name IS NOT NULL " +
                        "RETURN DISTINCT d.name AS Nume, d.uploadDate AS `Data încărcării`, d.releaseDate AS `Data eliberării`, CASE WHEN toLower(d.name) CONTAINS 'upu' THEN 'Fișă UPU' WHEN toLower(d.name) CONTAINS 'analize' THEN 'Analize Laborator' ELSE 'Document Medical' END AS Proprietăți " +
                        "ORDER BY `Data încărcării` DESC LIMIT 20";

                    List<Map<String, Object>> docRows = new ArrayList<>();
                    List<String> docCols;
                    try (Session session = driver.session()) {
                        Result result = session.run(cypher, Map.of("id", identifier));
                        docCols = result.keys();
                        while (result.hasNext()) {
                            docRows.add(result.next().asMap());
                        }
                    }
                    return ResponseEntity.ok(Map.of(
                            "reply_html", "<div>Iată lista documentelor tale:</div>" + buildHtml("Lista Documentelor Medicale", docCols, docRows)
                    ));
                }
            }

            // =====================================================
            // 3. CĂUTARE SEMANTICĂ (Dacă nu s-a cerut un document anume)
            // =====================================================
            if (!skipSemantic) {
                float[] primitiveVector = embeddingModel.embed(message);
                List<Double> queryVector = new ArrayList<>(primitiveVector.length);
                for (float v : primitiveVector) {
                    queryVector.add((double) v);
                }
                
                String semanticCypher = isPractitioner ?
                    "MATCH (:Practitioner {identifier: $id})-[:TREATS]->(p:Patient)<-[:subject]-(n)-[:source]->(d:Document) " :
                    "MATCH (p:Patient {identifier: $id})<-[:subject]-(n)-[:source]->(d:Document) ";
                    
                semanticCypher += 
                    "WHERE d.embedding IS NOT NULL AND size(d.embedding) = size($queryVector) " +
                    "WITH d, n, reduce(dot=0.0, i IN range(0, size(d.embedding)-1) | dot + d.embedding[i]*$queryVector[i]) AS sim " +
                    "ORDER BY sim DESC " +
                    "LIMIT 300 " +
                    "RETURN d.name AS Document, labels(n)[0] AS Type, n.display AS Display, n.valueQuantity_value AS Value, n.valueQuantity_unit AS Unit, n.dosageInstruction_text AS Dose, sim AS Similarity";

                try (Session session = driver.session()) {
                    Result result = session.run(semanticCypher, Map.of(
                            "id", identifier,
                            "queryVector", queryVector
                    ));
                    columns = result.keys();
                    while (result.hasNext()) {
                        Map<String, Object> row = result.next().asMap();
                        rows.add(row);
                        contextBuilder.append(row.toString()).append("\n");
                    }
                } catch (Exception e) {
                    return ResponseEntity.ok(Map.of("reply_html", "<div>Eroare la baza de date grafică: " + e.getMessage() + "</div>"));
                }
            }

            // Trecem datele extrase înapoi prin LLM pentru a compune un răspuns conversațional (RAG)
            String aiResponse = "";
            if (!rows.isEmpty()) {
                String ragPrompt = "Ești un asistent medical AI. Răspunde la întrebarea utilizatorului într-o propoziție, folosind STRICT datele de mai jos extrase prin căutare semantică din dosarul său medical.\n" +
                                   "Fii concis, clar, nu inventa date și la final recomandă un consult medical.\n\n" +
                                   "DATE MEDICALE GĂSITE:\n" + contextBuilder.toString() + "\n\n" +
                                   "ÎNTREBARE: " + message;
                try {
                    aiResponse = chatClient.prompt().user(ragPrompt).call().content();
                } catch (Exception e) {
                    aiResponse = "Iată ce am găsit în dosarul tău medical în legătură cu căutarea ta.";
                }
            } else {
                aiResponse = "Nu am găsit informații în dosarul medical referitor la această căutare.";
            }

            // Întoarcem răspunsul de la AI, alături de tabelul referințelor pentru afișare
            String finalHtml = "<div style='margin-bottom:15px; font-size:14px; line-height:1.5; color:#222;'>" + aiResponse + "</div>";
            if (!rows.isEmpty()) {
                finalHtml += buildHtml(tableTitle, columns, rows);
            }

            return ResponseEntity.ok(Map.of("reply_html", finalHtml));

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "reply_html", "<div>Server error: " + e.getMessage() + "</div>"
            ));
        }
    }

    // =====================================================
    // AUTH (REPLACE WITH JWT / SPRING SECURITY)
    // =====================================================
    private String getUserRole(String identifier) {
        try (Session session = driver.session()) {
            Result result = session.run(
                    "MATCH (u {identifier: $id}) WHERE u:Patient OR u:Practitioner RETURN labels(u)[0] AS role LIMIT 1",
                    Map.of("id", identifier)
            );
            if (result.hasNext()) {
                return result.next().get("role").asString();
            }
        } catch (Exception e) {
            System.err.println("Error fetching user role: " + e.getMessage());
        }
        return null;
    }

    // =====================================================
    // CLEAN CYPHER
    // =====================================================
    private String cleanCypher(String cypher) {
        cypher = cypher.replaceAll("(?i)```cypher|```", "").trim();

        int idx = indexOfFirstCypherKeyword(cypher);
        if (idx > 0) {
            cypher = cypher.substring(idx);
        }

        return cypher.trim();
    }

    private int indexOfFirstCypherKeyword(String c) {
        int[] idxs = {
                c.indexOf("MATCH"),
                c.indexOf("RETURN"),
                c.indexOf("WITH"),
                c.indexOf("CALL")
        };

        int min = Integer.MAX_VALUE;
        for (int i : idxs) {
            if (i >= 0 && i < min) min = i;
        }

        return min == Integer.MAX_VALUE ? -1 : min;
    }

    // =====================================================
    // VALIDATION
    // =====================================================
    private boolean isValidCypher(String cypher) {
        String c = cypher.trim().toLowerCase();
        return c.startsWith("match")
                || c.startsWith("return")
                || c.startsWith("with")
                || c.startsWith("call");
    }

    private boolean isDangerous(String cypher) {
        String lower = cypher.toLowerCase();
        return lower.contains(" delete ")
                || lower.contains(" detach ")
                || lower.contains(" create ")
                || lower.contains(" merge ")
                || lower.contains(" set ");
    }

    // =====================================================
    // HTML OUTPUT
    // =====================================================
    private String buildHtml(String title,
                         List<String> columns,
                         List<Map<String, Object>> rows) {

    StringBuilder html = new StringBuilder();

    // Details wrapper (collapsible accordion)
    html.append("""
        <details style="
            background:#ffffff;
            border-radius:12px;
            padding:12px;
            box-shadow:0 2px 8px rgba(0,0,0,0.08);
            max-width:100%;
            font-family:Arial;
            margin-top:10px;
        ">
        <summary style="
            cursor:pointer;
            color:#0f766e;
            font-weight:bold;
            font-size:14px;
            outline:none;
            user-select:none;
        ">
            📊 Afișează tabelul cu datele extrase
        </summary>
        <div style="margin-top:12px; overflow-x:auto;">
    """);

    // Title block
    html.append("<div style='font-size:12px;color:#666;margin-bottom:8px;'><b>")
        .append(title)
        .append("</b></div>");

    // Empty state
    if (rows.isEmpty()) {
        html.append("""
            <div style="margin-top:10px;color:#999;">
                Nu s-au găsit rezultate.
            </div>
        </div>
        </details>
        """);
        return html.toString();
    }

    // Table wrapper for scrolling
    html.append("""
        <table style="
            border-collapse:collapse;
            width:100%;
            font-size:13px;
        ">
        <thead>
        <tr>
    """);

    // Header
    for (String col : columns) {
        html.append("<th style='")
            .append("padding:8px;")
            .append("border-bottom:1px solid #ddd;")
            .append("background:#f3f4f6;")
            .append("text-align:left;")
            .append("white-space:nowrap;")
            .append("'>")
            .append(col)
            .append("</th>");
    }

    html.append("</tr></thead><tbody>");

    // Rows
    for (Map<String, Object> row : rows) {
        html.append("<tr>");

        for (String col : columns) {
            Object val = row.get(col);

            html.append("<td style='")
                .append("padding:8px;")
                .append("border-bottom:1px solid #eee;")
                .append("max-width:200px;")
                .append("overflow:hidden;")
                .append("text-overflow:ellipsis;")
                .append("white-space:nowrap;")
                .append("'>")
                .append(val != null ? val.toString() : "")
                .append("</td>");
        }

        html.append("</tr>");
    }

    html.append("""
        </tbody>
        </table>
        </div>
        </details>
    """);

    return html.toString();
}
}