package com.example.memgraph_api.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LlmExtractionService {

    private final ChatClient chatClient;
    private final Driver driver;

    public LlmExtractionService(ChatClient.Builder chatClientBuilder, Driver driver) {
        this.chatClient = chatClientBuilder.build();
        this.driver = driver;
    }

    public void processPdfAndStore(MultipartFile file, String identifier) throws Exception {
        String extractedText;
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            extractedText = stripper.getText(document);
        }

        // 2. Formulate the prompt for the LLM, include the identifier for user isolation
        String systemPrompt =
    "You are a medical informatics expert. Extract structured clinical data into Memgraph Cypher that is fully FHIR-aligned.\n\n" +

    "CRITICAL RULES:\n" +

    "1. ALLOWED LABELS:\n" +
    "Only use: Patient, Condition, Observation, MedicationRequest, Procedure.\n\n" +
    "Every node MUST include:\n" +
    "- resourceType (must exactly match label)\n" +
    "- code\n" +
    "- system (must be full URI)\n" +
    "- display\n\n" +

    "2. PATIENT (SINGLE SOURCE OF TRUTH — STRICT):\n" +
    "You MUST create or match ONLY ONE Patient node.\n\n" +
    "Always begin output with exactly:\n" +
    "MERGE (p:Patient {identifier: '" + identifier + "'})\n" +
    "ON CREATE SET p.resourceType = 'Patient';\n\n" +

    "IMPORTANT:\n" +
    "- Do NOT include any other properties in the MERGE.\n" +
    "- Do NOT create another Patient node under any circumstance.\n\n" +

    "3. PATIENT REUSE (MANDATORY):\n" +
    "Every subsequent statement MUST start with:\n" +
    "MATCH (p:Patient {identifier: '" + identifier + "'})\n" +
    "You MUST always reuse the SAME variable p.\n\n" +

    "4. RELATIONSHIP RULE (STRICT):\n" +
    "All clinical resources MUST connect to the patient like this:\n" +
    "(Resource)-[:subject]->(p)\n\n" +
    "- Use ONLY relationship type: subject\n" +
    "- NEVER create relationships to a new or inline Patient node\n" +
    "- NEVER add properties to relationships\n\n" +

    "5. CLINICAL NODE CREATION RULES:\n" +
    "Use MERGE (not CREATE) for all clinical entities.\n\n" +

    "Observation MERGE keys:\n" +
    "- code + system + valueQuantity_value + valueQuantity_unit\n\n" +

    "Condition MERGE keys:\n" +
    "- code + system\n\n" +

    "MedicationRequest MERGE keys:\n" +
    "- code + system + dosageInstruction_text\n\n" +

    "Procedure MERGE keys:\n" +
    "- code + system\n\n" +

    "6. DATA FLATTENING:\n" +
    "Use only flat properties:\n" +
    "- valueQuantity_value\n" +
    "- valueQuantity_unit\n" +
    "- dosageInstruction_text\n\n" +

    "Do NOT use nested objects.\n\n" +

    "7. DEDUPLICATION:\n" +
    "Never create duplicate nodes with the same identity keys.\n\n" +

    "8. OUTPUT FORMAT:\n" +
    "- ONLY raw Cypher\n" +
    "- NO markdown\n" +
    "- NO explanations\n" +
    "- Every statement ends with a semicolon\n\n" +

    "9. SAFETY RULE:\n" +
    "Do NOT redefine or recreate Patient anywhere in the output.\n\n" +

    "Medical Notes:\n" + extractedText;

        // String systemPrompt =
        // "You are a medical informatics expert. Extract data into 100% FHIR-compliant Cypher for Memgraph.\n\n" + 
        // "CRITICAL RULES:\n" +
        //  "1. LABELS & RESOURCE TYPE: Use ONLY Patient, Condition, Observation, MedicationRequest, and Procedure. " + 
        //  "Every node MUST have a property 'resourceType' that matches its Label exactly (e.g., {resourceType: 'Observation'}).\n" + 
        //  "2. PATIENT MAPPING: Start by matching the patient using the provided identifier: \n" +
        //   " MERGE (p:Patient {identifier: '" + identifier + "', resourceType: 'Patient'});\n" + 
        //   "3. RELATIONSHIP DIRECTION (STRICT): All resources must point TO the Patient.\n" +
        //    " - Format: (Resource)-[:subject]->(p)\n" + " - Example: MERGE (o:Observation {...})-[:subject]->(p);\n" +
        //    "4. RELATIONSHIP LABEL: Use ONLY 'subject' for all links to the patient.\n" + 
        //    "5. CODING: Every node MUST have 'resourceType', 'code', 'system', and 'display'. The 'system' MUST be a full URI (e.g., 'http://loinc.org' not 'LOINC', 'http://hl7.org/fhir/sid/icd-10' not 'ICD-10', 'http://www.nlm.nih.gov/research/umls/rxnorm' not 'RxNorm').\n" +
        //     "6. DATA FLATTENING: Store values as top-level properties (e.g., valueQuantity_value, valueQuantity_unit) rather than nested maps.\n" + 
        //     "7. EDGE PROPERTIES: Do NOT put properties on relationships. Clinical data belongs on nodes only.\n" + 
        //     "8. OUTPUT: Return ONLY raw Cypher. No markdown, no comments. End each statement with a semicolon.\n" + 
        //     "9. EXECUTION SAFETY: For every statement after the initial Patient MERGE, start with: " + 
        //     "MATCH (p:Patient {identifier: '" + identifier + "'}) " + "then use MERGE for the clinical resource. \n\n" + 
        //     "Medical Notes to process:\n" + extractedText;        
        
        // 3. Ask the LLM to generate Cypher
        String cypherQueries = chatClient.prompt()
            .user(systemPrompt)
            .call()
            .content();

        // Clean up the Cypher output
        cypherQueries = cypherQueries.replaceFirst("(?s)^.*?(?=(MERGE|CREATE|MATCH))", "");
        cypherQueries = cypherQueries.replace("```cypher", "").replace("```", "").trim();

        // Split by semicolon and execute each statement individually
        String[] statements = cypherQueries.split(";");
        String patientMatch = "MATCH (p:Patient {identifier: '" + identifier + "'}) ";
        try (Session session = driver.session()) {
            for (String stmt : statements) {
                String trimmed = stmt.trim();
                if (!trimmed.isEmpty()) {
                    // If statement creates a relationship to (p) but does not start with MATCH, inject the MATCH
                    if (trimmed.contains("-[:subject]->(p)") && !trimmed.startsWith("MATCH (p:Patient")) {
                        trimmed = patientMatch + trimmed;
                    }
                    session.run(trimmed);
                }
            }
        }
    }
}
        