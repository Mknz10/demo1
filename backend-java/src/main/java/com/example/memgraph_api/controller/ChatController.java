package com.example.memgraph_api.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final Driver driver;

    @Autowired
    public ChatController(ChatClient.Builder chatClientBuilder, Driver driver) {
        this.chatClient = chatClientBuilder.build();
        this.driver = driver;
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
            String patientId = getAuthenticatedPatientId();

            if (patientId == null) {
                return ResponseEntity.ok(Map.of(
                        "reply_html", "<div>No authenticated patient.</div>"
                ));
            }
// =====================================================
            // 2. DOCUMENT SEARCH & RELATION HANDLING
            // =====================================================
            String msgLower = message.toLowerCase();
            
            // If the user explicitly asks for a file/document, handle it securely.
            if (msgLower.contains("document") || msgLower.contains("file") || msgLower.contains("fișier")) {
                
                String fileName = null;
                String docId = null;
                
                try {
                    java.util.regex.Matcher mName = java.util.regex.Pattern.compile("(?:file|document|fișier)[^\\w\\d]*([\\w\\-. ]+\\.pdf)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(message);
                    if (mName.find()) fileName = mName.group(1).trim();
                    
                    java.util.regex.Matcher mId = java.util.regex.Pattern.compile("id[: ]+([\\w\\-]+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(message);
                    if (mId.find()) docId = mId.group(1).trim();
                } catch (Exception e) {
                    // ignore extraction errors
                }

                String cypher = null;

                if (fileName != null || docId != null) {
                    // FIX 1: Tie the document to the specific patient to prevent data leaks.
                    // FIX 2: Return primitive properties, NOT full nodes.
                    if (docId != null) {
                        cypher = "MATCH (p:Patient {identifier: '" + patientId + "'})<-[:subject]-(n)-[:source]->(d:Document {id: '" + docId + "'}) " +
                                 "RETURN labels(n)[0] AS ResourceType, n.display AS Display, n.code AS Code, n.valueQuantity_value AS Value, d.name AS DocumentName LIMIT 50";
                    } else {
                        cypher = "MATCH (p:Patient {identifier: '" + patientId + "'})<-[:subject]-(n)-[:source]->(d:Document {name: '" + fileName + "'}) " +
                                 "RETURN labels(n)[0] AS ResourceType, n.display AS Display, n.code AS Code, n.valueQuantity_value AS Value, d.name AS DocumentName LIMIT 50";
                    }
                } else {
                    // FIX 3: Secure the fallback query to only show THIS patient's documents.
                    // Assuming documents are connected to resources which point to the patient.
                    cypher = "MATCH (p:Patient {identifier: '" + patientId + "'})<-[:subject]-(n)-[:source]->(d:Document) " +
                             "WHERE d.name IS NOT NULL " +
                             "RETURN DISTINCT d.name AS Name, d.uploadDate AS UploadDate, d.releaseDate AS ReleaseDate, d.id AS ID " +
                             "ORDER BY d.uploadDate DESC LIMIT 20";
                }

                // Execute the safe, property-based query
                List<Map<String, Object>> rows = new ArrayList<>();
                List<String> columns;
                try (Session session = driver.session()) {
                    Result result = session.run(cypher);
                    columns = result.keys();
                    while (result.hasNext()) {
                        rows.add(result.next().asMap());
                    }
                }
                return ResponseEntity.ok(Map.of(
                        "reply_html", buildHtml(cypher, columns, rows)
                ));
            }

            // =====================================================
            // 3. BUILD STRICT CYPHER PROMPT
            // =====================================================
            String schemaPrompt =
                "You are a Cypher expert for a medical Memgraph database.\n" +
                "You generate ONLY READ-ONLY Cypher queries.\n\n" +

                "HARD RULES:\n" +
                "- NEVER use Patient.name for matching\n" +
                "- NEVER infer or search by name\n" +
                "- ALWAYS use ONLY this patient identifier:\n" +
                "  MATCH (p:Patient {identifier: '" + patientId + "'})\n" +
                "- NEVER output explanations or text\n" +
                "- Output ONLY Cypher starting with MATCH\n" +
                "- NEVER use CREATE, MERGE, DELETE, SET, DROP\n\n" +

                "CRITICAL RELATIONSHIP RULE:\n" +
                "- All clinical resources ALWAYS point TO patient\n" +
                "- Correct direction:\n" +
                "  (Resource)-[:subject]->(p:Patient)\n" +
                "- NEVER use (p)-[:subject]->(Resource)\n\n" +

                "SCHEMA:\n" +
                "- Observation(code, system, display, valueQuantity_value, valueQuantity_unit, valueBoolean, valueString, documentId)\n" +
                "- Condition(code, system, display, documentId)\n" +
                "- MedicationRequest(code, system, display, dosageInstruction_text, documentId)\n" +
                "- Procedure(code, system, display, documentId)\n\n" +

                "RELATIONSHIP MODEL:\n" +
                "(Observation|Condition|MedicationRequest|Procedure)-[:subject]->(p:Patient)\n\n" +

                "RETURN RULES (VERY IMPORTANT):\n" +
                "- NEVER return full nodes (NO: RETURN r, RETURN o, RETURN c)\n" +
                "- ALWAYS return properties only\n" +
                "- ALWAYS alias fields for readability\n" +
                "- Example correct return:\n" +
                "  RETURN o.display AS display, o.code AS code, o.valueQuantity_value AS value, o.valueQuantity_unit AS unit, o.documentId AS documentId\n\n" +

                "EXAMPLES:\n" +

                "Observations:\n" +
                "MATCH (o:Observation)-[:subject]->(p:Patient {identifier: '" + patientId + "'})\n" +
                "RETURN o.display AS display, o.code AS code, o.valueQuantity_value AS value, o.valueQuantity_unit AS unit, o.documentId AS documentId\n\n" +

                "Conditions:\n" +
                "MATCH (c:Condition)-[:subject]->(p:Patient {identifier: '" + patientId + "'})\n" +
                "RETURN c.display AS display, c.code AS code, c.system AS system, c.documentId AS documentId\n\n" +

                "MedicationRequests:\n" +
                "MATCH (m:MedicationRequest)-[:subject]->(p:Patient {identifier: '" + patientId + "'})\n" +
                "RETURN m.display AS display, m.code AS code, m.dosageInstruction_text AS dose, m.documentId AS documentId\n\n" +

                "Procedures:\n" +
                "MATCH (pr:Procedure)-[:subject]->(p:Patient {identifier: '" + patientId + "'})\n" +
                "RETURN pr.display AS display, pr.code AS code, pr.documentId AS documentId\n\n" +

                "USER QUESTION:\n" +
                message;

            // =====================================================
            // 3. CALL LLM
            // =====================================================
            String cypher;
            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> columns = new ArrayList<>();
            boolean fallback = false;
            try {
                cypher = chatClient.prompt()
                        .user(schemaPrompt)
                        .call()
                        .content();
            } catch (Exception e) {
                // fallback on LLM error
                fallback = true;
                cypher = null;
            }

            if (cypher == null || cypher.isBlank() || !isValidCypher(cleanCypher(cypher)) || isDangerous(cleanCypher(cypher))) {
                fallback = true;
            }

            if (!fallback) {
                // Try to execute the generated Cypher
                cypher = cleanCypher(cypher);
                try (Session session = driver.session()) {
                    Result result = session.run(cypher);
                    columns = result.keys();
                    while (result.hasNext()) {
                        rows.add(result.next().asMap());
                    }
                } catch (Exception e) {
                    // fallback on Cypher execution error (e.g., syntax)
                    fallback = true;
                }
            }

            // If fallback is needed or no results, show all observations and medications
            if (fallback || rows.isEmpty()) {
                // Try to show all observations and medications for the patient
                String fallbackCypher = "CALL {\n" +
                        "  MATCH (o:Observation)-[:subject]->(p:Patient {identifier: '" + patientId + "'})\n" +
                        "  RETURN 'Observation' AS type, o.display AS display, o.code AS code, o.valueQuantity_value AS value, o.valueQuantity_unit AS unit, o.documentId AS documentId\n" +
                        "  UNION\n" +
                        "  MATCH (m:MedicationRequest)-[:subject]->(p:Patient {identifier: '" + patientId + "'})\n" +
                        "  RETURN 'Medication' AS type, m.display AS display, m.code AS code, m.dosageInstruction_text AS value, '' AS unit, m.documentId AS documentId\n" +
                        "}\nRETURN type, display, code, value, unit, documentId";
                cypher = fallbackCypher;
                rows = new ArrayList<>();
                columns = new ArrayList<>();
                try (Session session = driver.session()) {
                    Result result = session.run(cypher);
                    columns = result.keys();
                    while (result.hasNext()) {
                        rows.add(result.next().asMap());
                    }
                } catch (Exception e) {
                    return ResponseEntity.ok(Map.of(
                            "reply_html", "<div>Server error: " + e.getMessage() + "</div>"
                    ));
                }
            }

            return ResponseEntity.ok(Map.of(
                    "reply_html", buildHtml(cypher, columns, rows)
            ));

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "reply_html", "<div>Server error: " + e.getMessage() + "</div>"
            ));
        }
    }

    // =====================================================
    // AUTH (REPLACE WITH JWT / SPRING SECURITY)
    // =====================================================
    private String getAuthenticatedPatientId() {
        // TODO: replace with real auth extraction
        return "6040815241248";
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
    private String buildHtml(String cypher,
                         List<String> columns,
                         List<Map<String, Object>> rows) {

    StringBuilder html = new StringBuilder();

    // Card wrapper (chat-friendly)
    html.append("""
        <div style="
            background:#ffffff;
            border-radius:12px;
            padding:12px;
            box-shadow:0 2px 8px rgba(0,0,0,0.08);
            max-width:100%;
            overflow-x:auto;
            font-family:Arial;
        ">
    """);

    // Cypher block
    html.append("""
        <div style="font-size:12px;color:#666;margin-bottom:8px;">
            <b>Cypher generat:</b>
        </div>
        <pre style="
            background:#f6f6f6;
            padding:8px;
            border-radius:8px;
            font-size:12px;
            overflow-x:auto;
        ">
    """).append(cypher).append("</pre>");

    // Empty state
    if (rows.isEmpty()) {
        html.append("""
            <div style="margin-top:10px;color:#999;">
                Nu s-au găsit rezultate.
            </div>
        </div>
        """);
        return html.toString();
    }

    // Table wrapper for scrolling
    html.append("""
        <div style="margin-top:10px; overflow-x:auto;">
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
        </div>
    """);

    return html.toString();
}
}