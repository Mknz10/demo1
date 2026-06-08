package com.example.memgraph_api.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LlmExtractionService {

    private final ChatClient chatClient;
    private final Driver driver;
    private final EmbeddingModel embeddingModel;

    public LlmExtractionService(ChatClient.Builder chatClientBuilder, Driver driver, EmbeddingModel embeddingModel) {
        this.chatClient = chatClientBuilder.build();
        this.driver = driver;
        this.embeddingModel = embeddingModel;
    }

    public void processPdfAndStore(MultipartFile file, String identifier) throws Exception {
        String extractedText;
        String fileName = file.getOriginalFilename();
        LocalDate uploadDate = LocalDate.now();

        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            extractedText = stripper.getText(document);
        }

        LocalDate parsedDate = extractDateFromText(extractedText);
        LocalDate releaseDate = parsedDate != null ? parsedDate : uploadDate;

        // 1. Generare vector de context (Embedding) pentru document
        // Limităm textul la 20k caractere pentru a nu depăși contextul maxim acceptat de OpenAI
        String textForEmbedding = extractedText.length() > 20000 ? extractedText.substring(0, 20000) : extractedText;
        float[] primitiveVector = embeddingModel.embed(textForEmbedding);
        List<Double> embeddingVector = new ArrayList<>(primitiveVector.length);
        for (float v : primitiveVector) {
            embeddingVector.add((double) v);
        }

        // 2. Create Document node Cypher
        String docId = identifier + "_" + uploadDate.toString() + "_" + fileName;
        String docName = fileName;
        String createDocumentCypher = "MERGE (d:Document {id: $docId}) " +
            "SET d.name = $docName, d.uploadDate = $uploadDate, d.releaseDate = $releaseDate, d.embedding = $embeddingVector";

        // 3. Formulate the prompt for the LLM, include the identifier and docId for user isolation
        String systemPrompt =
            "You are a medical informatics expert. Extract structured clinical data into Memgraph Cypher that is fully FHIR-aligned.\n\n" +
            "CRITICAL RULES:\n" +
            "1. ALLOWED LABELS:\n" +
            "Only use: Patient, Condition, Observation, MedicationAdministration, MedicationRequest, Procedure.\n\n" +
            "Every node MUST include:\n" +
            "- resourceType (must exactly match label)\n" +
            "- id (generate a random UUID for each new node)\n" +
            "- name (the clinical display name)\n" +
            "- status (e.g., 'active', 'completed', 'N/A')\n" +
            "- documentId (must be set to '" + docId + "')\n\n" +
            "2. PATIENT (SINGLE SOURCE OF TRUTH — STRICT):\n" +
            "You MUST create or match ONLY ONE Patient node.\n\n" +
            "Always begin output with exactly:\n" +
            "MERGE (p:Patient {id: '" + identifier + "'})\n" +
            "ON CREATE SET p.resourceType = 'Patient';\n\n" +
            "IMPORTANT:\n" +
            "- Do NOT include any other properties in the MERGE.\n" +
            "- Do NOT create another Patient node under any circumstance.\n\n" +
            "3. PATIENT REUSE (MANDATORY):\n" +
            "Every subsequent statement MUST start with:\n" +
            "MATCH (p:Patient {id: '" + identifier + "'})\n" +
            "You MUST always reuse the SAME variable p.\n\n" +
            "4. RELATIONSHIP RULE (STRICT):\n" +
            "All clinical resources MUST connect FROM the patient TO the resource like this:\n" +
            "(p)-[:HAS_<RESOURCETYPE_UPPERCASE>]->(Resource)\n\n" +
            "Example for Observation: (p)-[:HAS_OBSERVATION]->(o)\n" +
            "Example for Condition: (p)-[:HAS_CONDITION]->(c)\n\n" +
            "- NEVER create relationships to a new or inline Patient node\n" +
            "- NEVER add properties to relationships\n\n" +
            "5. CLINICAL NODE CREATION RULES:\n" +
            "Use MERGE based on BOTH the `name` AND `documentId` properties.\n\n" +
            "Distinguish between medications:\n" +
            "- Use `MedicationRequest` for prescriptions, home medications, or discharge recommendations.\n" +
            "- Use `MedicationAdministration` for IVs, injections, or medications actually administered in the clinic/hospital.\n\n" +
            "Example:\n" +
            "MERGE (o:Observation {name: 'Hemoglobin: 14.2 g/dL', documentId: '" + docId + "'})\n" +
            "ON CREATE SET o.id = randomUUID(), o.status = 'final', o.resourceType = 'Observation'\n" +
            "// CRITICAL: For Observations (lab results, vitals), the 'name' property MUST include the measured value and unit!\n" +
            "MERGE (p)-[:HAS_OBSERVATION]->(o);\n\n" +
            "6. DATA FLATTENING:\n" +
            "Use only flat properties (id, name, status, resourceType, documentId).\n" +
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
            "10. EXHAUSTIVE EXTRACTION (CRITICAL):\n" +
            "You MUST NOT skip any clinical data, especially lab results.\n" +
            "If you see a list of blood tests, urinalysis or observations (e.g., Neutrofile, Limfocite, INR, Eozinofile, Glicemie), you MUST create a separate Observation node for EVERY SINGLE line item.\n" +
            "Do not summarize or skip anything to save space. Be 100% exhaustive.\n\n" +
            "11. TRANSLATION TO ENGLISH (MANDATORY):\n" +
            "You MUST translate ALL medical terms, test names, diagnoses, and procedures into ENGLISH.\n" +
            "For example, instead of 'Tensiune arterială: 110/70 mmHg', you MUST output 'Blood Pressure: 110/70 mmHg'.\n" +
            "Keep numerical values and medical units exactly as they appear in the text.\n\n" +
            "Medical Notes:\n" + extractedText;
            
        // 3. Ask the LLM to generate Cypher
        String cypherQueries = chatClient.prompt()
            .user(systemPrompt)
            .call()
            .content();

        if (cypherQueries == null) {
            throw new IllegalStateException("Cypher generation failed: LLM returned null");
        }

        // Clean up the Cypher output
        cypherQueries = cypherQueries.replaceFirst("(?s)^.*?(?=(MERGE|CREATE|MATCH))", "");
        cypherQueries = cypherQueries.replace("```cypher", "").replace("```", "").trim();

        // Split by semicolon and execute each statement individually
        String[] statements = cypherQueries.split(";");
        String patientMatch = "MATCH (p:Patient {id: '" + identifier + "'}) ";
        Map<String, Object> docParams = new HashMap<>();
        docParams.put("docId", docId);
        docParams.put("docName", docName);
        docParams.put("uploadDate", uploadDate.toString());
        docParams.put("releaseDate", releaseDate.toString());
        docParams.put("embeddingVector", embeddingVector);

        try (Session session = driver.session()) {
            
            // 1. Create or update the Document node first
            session.run(createDocumentCypher, docParams);
            
            // 2. Execute the LLM generated statements
            for (String stmt : statements) {
                String trimmed = stmt.trim();
                if (!trimmed.isEmpty()) {
                    // Try to inject MATCH p if missing and it tries to link to p
                    if (trimmed.contains("(p)-[:HAS_") && !trimmed.startsWith("MATCH (p:Patient")) {
                        trimmed = patientMatch + trimmed;
                    }
                    try {
                        session.run(trimmed);
                    } catch (Exception e) {
                        // Log LLM syntax errors but don't crash the whole extraction
                        System.err.println("Skipped malformed LLM Cypher: " + trimmed);
                    }
                }
            }
            
            // 3. BULLETPROOF FALLBACK (Fixes floating nodes)
            // Because the LLM successfully sets the `documentId` property on the nodes,
            // we can safely query for them and force the relationships to exist.
            String[] types = {"Observation", "Condition", "MedicationAdministration", "MedicationRequest", "Procedure"};
            for (String type : types) {
                
                // Step A: Guarantee the node is linked to the Patient
                String relType = "HAS_" + type.toUpperCase();
                String linkToPatient = 
                    "MATCH (n:" + type + " {documentId: '" + docId + "'}) " +
                    "MATCH (p:Patient {id: '" + identifier + "'}) " +
                    "MERGE (p)-[:" + relType + "]->(n)";
                session.run(linkToPatient);

                // Step B: Guarantee the node is linked to the Document
                String linkToDocument = 
                    "MATCH (n:" + type + " {documentId: '" + docId + "'}) " +
                    "MATCH (d:Document {id: '" + docId + "'}) " +
                    "MERGE (n)-[:source]->(d)";
                session.run(linkToDocument);
            }
            
            // 4. CLEANUP HALUCINATIONS (Anti-Noduri Aiurea)
            // Curățăm orice nod pe care AI-ul l-a inventat și care nu respectă modelul oficial din baza de date
            String cleanupCypher = 
                "MATCH (n) WHERE n.documentId = $docId " +
                "AND NOT n:Patient AND NOT n:Document " +
                "AND NOT n:Condition AND NOT n:Observation AND NOT n:MedicationAdministration AND NOT n:MedicationRequest AND NOT n:Procedure " +
                "DETACH DELETE n";
            session.run(cleanupCypher, docParams);
        }
    }

    private LocalDate extractDateFromText(String text) {
        if (text == null) return null;
        
        // Căutăm date calendaristice precedate de cuvinte cheie specifice (ex: Data emiterii, Data recoltării, Date)
        java.util.regex.Pattern keywordPattern = java.util.regex.Pattern.compile(
            "(?i)(?:data|date|emis|eliberat|recoltat)[\\s:A-Za-zăîâșțĂÎÂȘȚ]*(\\d{2}[./-]\\d{2}[./-]\\d{4}|\\d{4}-\\d{2}-\\d{2})"
        );
        java.util.regex.Matcher keywordMatcher = keywordPattern.matcher(text);
        
        String dateStr = null;
        if (keywordMatcher.find()) {
            dateStr = keywordMatcher.group(1);
        } else {
            // Fallback: prima dată calendaristică validă găsită în text (ex. dacă lipsește cuvântul cheie)
            java.util.regex.Pattern fallbackPattern = java.util.regex.Pattern.compile("\\b(\\d{2}[./-]\\d{2}[./-]\\d{4}|\\d{4}-\\d{2}-\\d{2})\\b");
            java.util.regex.Matcher fallbackMatcher = fallbackPattern.matcher(text);
            if (fallbackMatcher.find()) {
                dateStr = fallbackMatcher.group(1);
            }
        }

        if (dateStr != null) {
            try {
                if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    return LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                } else if (dateStr.contains(".")) {
                    return LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                } else if (dateStr.contains("/")) {
                    return LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                } else if (dateStr.contains("-")) {
                    return LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                }
            } catch (Exception e) {
                // Ignorăm erorile de parsare
            }
        }
        return null;
    }
}
        