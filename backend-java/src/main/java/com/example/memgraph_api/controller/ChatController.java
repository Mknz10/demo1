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
            // 2. BUILD STRICT CYPHER PROMPT
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
                "- Observation(code, system, display, valueQuantity_value, valueQuantity_unit, valueBoolean, valueString)\n" +
                "- Condition(code, system, display)\n" +
                "- MedicationRequest(code, system, display, dosageInstruction_text)\n" +
                "- Procedure(code, system, display)\n\n" +

                "RELATIONSHIP MODEL:\n" +
                "(Observation|Condition|MedicationRequest|Procedure)-[:subject]->(p:Patient)\n\n" +

                "RETURN RULES (VERY IMPORTANT):\n" +
                "- NEVER return full nodes (NO: RETURN r, RETURN o, RETURN c)\n" +
                "- ALWAYS return properties only\n" +
                "- ALWAYS alias fields for readability\n" +
                "- Example correct return:\n" +
                "  RETURN o.display AS display, o.code AS code, o.valueQuantity_value AS value, o.valueQuantity_unit AS unit\n\n" +

                "EXAMPLES:\n" +

                "Observations:\n" +
                "MATCH (o:Observation)-[:subject]->(p:Patient {identifier: '" + patientId + "'})\n" +
                "RETURN o.display AS display, o.code AS code, o.valueQuantity_value AS value, o.valueQuantity_unit AS unit\n\n" +

                "Conditions:\n" +
                "MATCH (c:Condition)-[:subject]->(p:Patient {identifier: '" + patientId + "'})\n" +
                "RETURN c.display AS display, c.code AS code, c.system AS system\n\n" +

                "MedicationRequests:\n" +
                "MATCH (m:MedicationRequest)-[:subject]->(p:Patient {identifier: '" + patientId + "'})\n" +
                "RETURN m.display AS display, m.code AS code, m.dosageInstruction_text AS dose\n\n" +

                "Procedures:\n" +
                "MATCH (pr:Procedure)-[:subject]->(p:Patient {identifier: '" + patientId + "'})\n" +
                "RETURN pr.display AS display, pr.code AS code\n\n" +

                "USER QUESTION:\n" +
                message;

            // =====================================================
            // 3. CALL LLM
            // =====================================================
            String cypher;
            try {
                cypher = chatClient.prompt()
                        .user(schemaPrompt)
                        .call()
                        .content();
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of(
                        "reply_html", "<div>LLM error: " + e.getMessage() + "</div>"
                ));
            }

            if (cypher == null || cypher.isBlank()) {
                return ResponseEntity.ok(Map.of(
                        "reply_html", "<div>LLM returned empty response.</div>"
                ));
            }

            // =====================================================
            // 4. CLEAN + VALIDATE CYPHER
            // =====================================================
            cypher = cleanCypher(cypher);

            if (!isValidCypher(cypher)) {
                return ResponseEntity.ok(Map.of(
                        "reply_html", "<div>Invalid Cypher generated.</div>",
                        "raw", cypher
                ));
            }

            if (isDangerous(cypher)) {
                return ResponseEntity.ok(Map.of(
                        "reply_html", "<div>Blocked unsafe query.</div>",
                        "cypher", cypher
                ));
            }

            // =====================================================
            // 5. EXECUTE QUERY
            // =====================================================
            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> columns;

            try (Session session = driver.session()) {
                Result result = session.run(cypher);
                columns = result.keys();

                while (result.hasNext()) {
                    rows.add(result.next().asMap());
                }
            }

            // =====================================================
            // 6. FORMAT RESPONSE
            // =====================================================
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