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
        String history = payload.getOrDefault("history", "");

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

            // =====================================================
            // 1.5. EXTRAGERE DATE PERSONALE PENTRU CONTEXT (Cine sunt?)
            // =====================================================
            String userInfo = "Informații personale indisponibile.";
            try (Session session = driver.session()) {
                Result res = session.run(
                    "MATCH (u) WHERE u.id = $id RETURN labels(u)[0] AS role, u.name AS name, u.name_family AS family, u.name_given AS given, u.gender AS gen, u.birthDate AS nastere",
                    Map.of("id", identifier)
                );
                if (res.hasNext()) {
                    var rec = res.next();
                    String roleFromDb = rec.get("role").asString();
                    
                    String uName = "Pacient";
                    if (!rec.get("name").isNull()) {
                        uName = rec.get("name").asString();
                    } else if (!rec.get("family").isNull() && !rec.get("given").isNull()) {
                        List<Object> givenList = rec.get("given").asList();
                        String givenName = givenList.isEmpty() ? "" : givenList.get(0).toString();
                        uName = givenName + " " + rec.get("family").asString();
                    }
                    
                    if ("Patient".equals(roleFromDb)) {
                        String uGen = rec.get("gen").isNull() ? "Necunoscut" : rec.get("gen").asString();
                        String uNastere = rec.get("nastere").isNull() ? "Necunoscut" : rec.get("nastere").asString();
                        userInfo = "Nume: " + uName + " | Gen: " + uGen + " | Data Nașterii: " + uNastere;

                        // Aflăm medicii cu acces la dosar
                        Result docRes = session.run("MATCH (pr:Practitioner)-[:TREATS]->(p:Patient {id: $id}) RETURN coalesce(pr.name, pr.name_family, 'Medic ' + pr.id) AS docName", Map.of("id", identifier));
                        List<String> docs = new ArrayList<>();
                        while (docRes.hasNext()) {
                            docs.add(docRes.next().get("docName").asString());
                        }
                        if (!docs.isEmpty()) {
                            userInfo += " | Medici curanți (cu acces la date): " + String.join(", ", docs);
                        } else {
                            userInfo += " | Medici curanți (cu acces la date): Niciunul";
                        }
                    } else {
                        userInfo = "Nume (Medic): " + uName;
                        
                        // Aflăm pacienții acestui medic (doar cei care i-au oferit acces)
                        Result patRes = session.run("MATCH (pr:Practitioner {id: $id})-[r:TREATS]->(p:Patient) WHERE coalesce(r.hasAccess, true) = true RETURN coalesce(p.name, p.name_family, 'Pacient ID: ' + p.id) AS patName", Map.of("id", identifier));
                        List<String> patientsList = new ArrayList<>();
                        while (patRes.hasNext()) {
                            patientsList.add(patRes.next().get("patName").asString());
                        }
                        if (!patientsList.isEmpty()) {
                            userInfo += " | Pacienți asociați (cu acces permis): " + String.join(", ", patientsList);
                        } else {
                            userInfo += " | Pacienți asociați: Niciunul în acest moment.";
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Eroare la extragerea datelor personale: " + e.getMessage());
            }

            boolean isPractitioner = "Practitioner".equals(role);
            String baseMatch = isPractitioner 
                ? "MATCH (pr:Practitioner)-[:TREATS]->(p:Patient) WHERE pr.id = '" + identifier + "'" 
                : "MATCH (p:Patient) WHERE p.id = '" + identifier + "'";

            // =====================================================
            // 2. PREGĂTIRE CONTEXT PENTRU RAG
            // =====================================================
            String msgLower = message.toLowerCase();
            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> columns = new ArrayList<>();
            StringBuilder contextBuilder = new StringBuilder();
            String tableTitle = "Căutare Vectorială Semantică";
            
            // Verificăm dacă utilizatorul dorește o listă a documentelor
            boolean listDocsIntent = msgLower.matches(".*\\b(lista|arat[aă]|toate|ce documente)\\b.*") && msgLower.contains("document");
            
            if (listDocsIntent) {
                String cypher = isPractitioner ?
                    "MATCH (pr:Practitioner)-[r:TREATS]->(p:Patient)-[]->(n)-[:source]->(d:Document) WHERE pr.id = $id AND coalesce(r.hasAccess, true) = true " +
                    "AND d.name IS NOT NULL " +
                    "RETURN DISTINCT d.name AS Nume, coalesce(p.name, p.name_family, 'Pacient ' + p.id) AS Pacient, d.uploadDate AS `Data încărcării`, d.releaseDate AS `Data eliberării`, CASE WHEN toLower(d.name) CONTAINS 'upu' THEN 'Fișă UPU' WHEN toLower(d.name) CONTAINS 'analize' THEN 'Analize Laborator' ELSE 'Document Medical' END AS Proprietăți " +
                    "ORDER BY `Data încărcării` DESC LIMIT 20" :
                    "MATCH (p:Patient)-[]->(n)-[:source]->(d:Document) WHERE p.id = $id " +
                    "AND d.name IS NOT NULL " +
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

            // Detectăm dacă a fost solicitat un document anume
            String targetDocId = null;
            String targetDocName = null;

            if (msgLower.contains("document") || msgLower.contains("file") || msgLower.contains("fișier") || msgLower.contains("pdf")) {
                try (Session session = driver.session()) {
                    String docQuery = isPractitioner ?
                        "MATCH (pr:Practitioner)-[r:TREATS]->(p:Patient)-[]->(n)-[:source]->(d:Document) WHERE pr.id = $id AND coalesce(r.hasAccess, true) = true RETURN DISTINCT d.id AS docId, d.name AS docName" :
                        "MATCH (p:Patient)-[]->(n)-[:source]->(d:Document) WHERE p.id = $id RETURN DISTINCT d.id AS docId, d.name AS docName";

                    Result docRes = session.run(docQuery, Map.of("id", identifier));
                    int bestMatchLength = 0;
                    while (docRes.hasNext()) {
                        var record = docRes.next();
                        String dName = record.get("docName").asString();
                        String dId = record.get("docId").asString();

                        String dNameLower = dName.toLowerCase();
                        String dNameNoExt = dNameLower.replaceAll("\\.(pdf|jpg|png|jpeg)$", "").trim();

                        if (msgLower.contains(dNameLower) && dNameLower.length() > bestMatchLength) {
                            targetDocId = dId;
                            targetDocName = dName;
                            bestMatchLength = dNameLower.length();
                        } else if (msgLower.contains(dNameNoExt) && dNameNoExt.length() > bestMatchLength) {
                            targetDocId = dId;
                            targetDocName = dName;
                            bestMatchLength = dNameNoExt.length();
                        }
                    }
                }
            }

            // =====================================================
            // 3. EXTRAGERE DATE DOSAR (Compatibil MIMIC-IV)
            // =====================================================
            boolean isCategorySpecific = false;
            String filterLabels = "['Condition', 'ConditionED', 'Medication', 'MedicationAdministration', 'MedicationAdministrationICU', 'MedicationDispense', 'MedicationDispenseED', 'MedicationMix', 'MedicationRequest', 'MedicationStatementED', 'Observation', 'ObservationChartevents', 'ObservationDatetimeevents', 'ObservationED', 'ObservationLabevents', 'ObservationMicroOrg', 'ObservationMicroSusc', 'ObservationMicroTest', 'ObservationOutputevents', 'ObservationVitalSignsED', 'Procedure', 'ProcedureED', 'ProcedureICU']";
            
            if (msgLower.contains("medicament") || msgLower.contains("tratament") || msgLower.contains("pastil") || msgLower.contains("reteta") || msgLower.contains("rețetă")) {
                filterLabels = "['Medication', 'MedicationAdministration', 'MedicationAdministrationICU', 'MedicationDispense', 'MedicationDispenseED', 'MedicationMix', 'MedicationRequest', 'MedicationStatementED']";
                isCategorySpecific = true;
            } else if (msgLower.contains("analiz") || msgLower.contains("laborator") || msgLower.contains("rezultat") || msgLower.contains("test")) {
                filterLabels = "['Observation', 'ObservationChartevents', 'ObservationDatetimeevents', 'ObservationED', 'ObservationLabevents', 'ObservationMicroOrg', 'ObservationMicroSusc', 'ObservationMicroTest', 'ObservationOutputevents', 'ObservationVitalSignsED']";
                isCategorySpecific = true;
            } else if (msgLower.contains("diagnostic") || msgLower.contains("boal") || msgLower.contains("afecțiun") || msgLower.contains("afectiun")) {
                filterLabels = "['Condition', 'ConditionED']";
                isCategorySpecific = true;
            } else if (msgLower.contains("procedur") || msgLower.contains("operati") || msgLower.contains("operați") || msgLower.contains("interventi") || msgLower.contains("intervenți")) {
                filterLabels = "['Procedure', 'ProcedureED', 'ProcedureICU']";
                isCategorySpecific = true;
            } else if (msgLower.matches("(?i).*\\b(cine sunt|cum mă cheamă|câți ani am|vârsta mea|datele mele|acces|medic|doctor)\\b.*") && !msgLower.contains("medicament")) {
                filterLabels = "[]"; 
                isCategorySpecific = true;
            } else if (msgLower.matches("(?i).*\\b(pacient|pacienți|pacienti|comun|asemănare|asemanare|toți|toti|ambii|amândoi|amandoi|diferenț|diferent)\\b.*")) {
                // Păstrăm toate categoriile medicale dar trecem de pragul de similaritate pentru a aduce afecțiunile tuturor
                isCategorySpecific = true;
            }

            String dbCypher = isPractitioner ?
                "MATCH (pr:Practitioner)-[r:TREATS]->(p:Patient) WHERE pr.id = $id AND coalesce(r.hasAccess, true) = true \n" :
                "MATCH (p:Patient) WHERE p.id = $id \n";

            Map<String, Object> queryParams = new java.util.HashMap<>();
            queryParams.put("id", identifier);

            if (targetDocId != null) {
                dbCypher += "MATCH (p)-[]->(n)-[:source]->(d:Document {id: $docId}) \n";
                queryParams.put("docId", targetDocId);
                queryParams.put("docName", targetDocName);
                tableTitle = "Extragere din: " + targetDocName;
                dbCypher += "WHERE labels(n)[0] IN " + filterLabels + " AND n.name IS NOT NULL AND NOT n.name CONTAINS 'Generic' \n";
                dbCypher += "WITH labels(n)[0] AS Type, n.name AS Name, collect(n)[0] AS node, p, d \n";
                dbCypher += "RETURN Type, node.name AS Nume, $docName AS Document, coalesce(p.name, p.name_family, 'Pacient ' + p.id) AS Pacient \n";
            } else {
                dbCypher += "MATCH (p)-[]->(n) \n";
                dbCypher += "WHERE labels(n)[0] IN " + filterLabels + " AND n.name IS NOT NULL AND NOT n.name CONTAINS 'Generic' \n";
                dbCypher += "OPTIONAL MATCH (n)-[:source]->(d:Document) \n";
                dbCypher += "WITH labels(n)[0] AS Type, n.name AS Name, collect(n)[0] AS node, p, d \n";
                dbCypher += "RETURN Type, node.name AS Nume, coalesce(d.name, 'Sursă externă') AS Document, coalesce(p.name, p.name_family, 'Pacient ' + p.id) AS Pacient \n";
            }
            
            // Extragem o plajă mai mare de date pentru a nu tăia pacienții în cazul în care primul pacient are sute de afecțiuni
            dbCypher += "LIMIT 1000";

            try (Session session = driver.session()) {
                Result result = session.run(dbCypher, queryParams);
                columns = new ArrayList<>(result.keys());
                columns.add("Potrivire Semantică");
                while (result.hasNext()) {
                    Map<String, Object> row = new java.util.HashMap<>(result.next().asMap());
                    rows.add(row);
                }
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of("reply_html", "<div>Eroare la extragerea datelor: " + e.getMessage() + "</div>"));
            }

            // =====================================================
            // CALCUL POTRIVIRE SEMANTICĂ (COSINE SIMILARITY)
            // =====================================================
            if (!rows.isEmpty()) {
                float[] queryEmbedding = null;
                try {
                    queryEmbedding = embeddingModel.embed(message);
                } catch (Exception e) {
                    System.err.println("Eroare la generarea vectorului pentru întrebare: " + e.getMessage());
                }

                List<Map<String, Object>> filteredRows = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    String nume = (String) row.get("Nume");
                    String pacient = (String) row.get("Pacient");
                    if (queryEmbedding != null && nume != null) {
                        try {
                            float[] numeEmbedding = embeddingModel.embed(nume);
                            double sim = cosineSimilarity(queryEmbedding, numeEmbedding);
                            int percent = (int) Math.round(sim * 100);
                            
                            // Căutare Hibridă: Verificăm dacă vreun cuvânt cheie (>= 4 litere) din nume se regăsește explicit în întrebare
                            boolean hasKeywordMatch = false;
                            String numeClean = nume.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
                            for (String w : numeClean.split("\\s+")) {
                                if (w.length() >= 4 && msgLower.matches(".*\\b" + w + "\\b.*")) {
                                    hasKeywordMatch = true;
                                    break;
                                }
                            }

                            // Căutare Hibridă: Verificăm dacă medicul a întrebat explicit de un anumit pacient (pe nume)
                            if (isPractitioner && pacient != null) {
                                String pacientClean = pacient.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").replace("pacient", "");
                                for (String w : pacientClean.split("\\s+")) {
                                    // Folosim minim 3 litere pentru nume de familie scurte (ex: Pop, Doe, Ion)
                                    if (w.length() >= 3 && msgLower.matches(".*\\b" + w + "\\b.*")) {
                                        hasKeywordMatch = true;
                                        break;
                                    }
                                }
                            }

                            // Dacă s-a cerut o categorie anume sau un document anume, bypassăm pragul minim. Altfel e 45%.
                            int threshold = (targetDocId != null || isCategorySpecific) ? 0 : 45;
                            
                            if (percent >= threshold || hasKeywordMatch) {
                                row.put("Potrivire Semantică", percent + "%");
                                // Combinăm scorul hibrid cu cel semantic pentru a menține sortarea relevantă la final
                                row.put("scoreRaw", hasKeywordMatch ? (1.0 + sim) : sim);
                                filteredRows.add(row);
                            }
                        } catch (Exception e) {
                            row.put("Potrivire Semantică", "N/A");
                            row.put("scoreRaw", 0.0);
                            filteredRows.add(row);
                        }
                    } else {
                        row.put("Potrivire Semantică", "N/A");
                        row.put("scoreRaw", 0.0);
                        filteredRows.add(row);
                    }
                }
                rows = filteredRows;

                // Sortăm descrescător tabelul după scorul de potrivire
                rows.sort((r1, r2) -> Double.compare(
                        (Double) r2.getOrDefault("scoreRaw", 0.0),
                        (Double) r1.getOrDefault("scoreRaw", 0.0)
                ));
                
                // Limităm strict la cele mai relevante 120 de intrări pentru a nu depăși contextul maxim acceptat de LLM
                if (rows.size() > 120) {
                    rows = rows.subList(0, 120);
                }

                // Construim textul pentru LLM punând primele cele mai relevante date
                contextBuilder.setLength(0);
                for (Map<String, Object> row : rows) {
                    row.remove("scoreRaw"); // Ștergem valoarea tehnică să nu deruteze LLM-ul
                    contextBuilder.append(row.toString()).append("\n");
                }
            } else if (targetDocId != null) {
                return ResponseEntity.ok(Map.of("reply_html", 
                    "<div style='margin-bottom:15px; font-size:14px; line-height:1.5; color:#222;'>" +
                    "Nu am găsit informații în categoria solicitată pentru documentul <b>" + targetDocName + "</b>. " +
                    "Încearcă o altă formulare sau verifică lista de documente.</div>"
                ));
            }

            // Trecem datele extrase înapoi prin LLM pentru a compune un răspuns conversațional (RAG)
            String aiResponse = "";
            String ragPrompt = "";

            String contextData = contextBuilder.toString();
            // Protect against payload size limits (Groq 400 Bad Request error)
            if (contextData.length() > 15000) {
                contextData = contextData.substring(0, 15000) + "\n... [DATE TRUNCHIATE DIN CAUZA LIMITELOR DE DIMENSIUNE] ...";
            }

            String disclaimerRule = isPractitioner
                ? "Dacă o informație lipsește, menționează că nu există în datele disponibile. Deoarece vorbești cu un medic, nu recomanda consult medical la final și nu formula concluzii clinice nesusținute de date.\n\n"
                : "Dacă o informație lipsește, explică faptul că nu există în datele disponibile. Răspunsul are rol informativ și nu înlocuiește consultarea unui medic.\n\n";

            ragPrompt = "Ești un asistent medical AI profesionist și empatic. Fii concis, dar natural și prietenos în exprimare.\n" +
                               "Dacă utilizatorul pune întrebări personale (ex: cine sunt, vârsta mea, cine are acces la date, cine sunt pacienții mei), răspunde folosind exclusiv DATELE PERSONALE.\n" +
                               "Dacă pune întrebări medicale, ÎNCEPE MEREU răspunsul cu o afirmație clară și directă (ex: 'Da, pacienta are analize de urină...', 'Nu, nu am găsit...'). Abia apoi detaliază rezultatele.\n" +
                               "FORMATARE MARKDOWN STRICTĂ: Dacă afișezi date medicale, folosește OBLIGATORIU o listă cu marcatori ('- '). Lasă OBLIGATORIU un rând liber (dublu ENTER) înainte de a începe lista, dar și între numele pacientului și listă. NU afișa valori goale sau fără rezultat (ex: ignoră 'Nitrite: -' dacă nu are nicio valoare). NU menționa ID-uri tehnice sau scoruri.\n" +
                               "REGULĂ DE LIMBĂ: Răspunde OBLIGATORIU în aceeași limbă în care a fost formulată ÎNTREBAREA. Dacă întrebarea e în română, răspunde în română (traducând datele extrase din engleză înapoi în română). Dacă e în engleză, răspunde în engleză.\n" +
                               "ATENȚIE MAXIMĂ LA NUMERE: Când prezinți valori medicale (rezultate analize, doze), copiază-le EXACT așa cum apar în DATE MEDICALE GĂSITE. Nu inversa cifrele (ex: 3.87 NU trebuie să devină 3.78) și nu rotunji valorile.\n" +
                               "SEPARARE PACIENȚI: Dacă ești medic și ai date de la mai mulți pacienți, fii foarte atent la coloana 'Pacient' din datele găsite. NU amesteca analizele/afecțiunile între ei. Atribuie fiecare diagnostic strict pacientului indicat.\n" +
                               "SURSĂ DATE: Dacă utilizatorul te întreabă din ce document provin anumite date, folosește informația din coloana 'Document' a tabelului extras sau uită-te în Istoricul Conversației pentru a face legătura.\n" +
                               "SEPARAREA SURSELOR: Datele pot proveni din setul MIMIC/FHIR sau din documente medicale încărcate ulterior. " +
                               "Nu amesteca informațiile provenite din surse diferite dacă nu există o legătură clară în datele returnate. " +
                               "Dacă sursa este necunoscută sau apare ca 'Sursă externă', menționează că informația provine din datele disponibile în graf, fără a inventa documentul sursă.\n" +
                               "SEMANTICA ÎNTREBĂRII: Respectă cu atenție formularea utilizatorului. " +
                               "Dacă întrebarea conține 'și', 'atât... cât și' sau 'ambele', tratează condițiile ca fiind cumulative. " +
                               "Dacă întrebarea conține 'sau' sau 'oricare', tratează condițiile ca alternative. " +
                               "Nu transforma o cerință cumulativă într-una alternativă.\n" +
                               "DEDUCȚIE MEDICALĂ: Nu presupune cauzalități medicale care nu apar explicit în datele găsite. " +
                               "Dacă utilizatorul întreabă de ce a fost administrat un tratament, explică doar informațiile susținute de contextul disponibil. " +
                               "Dacă nu există o legătură clară între diagnostic, episod clinic și medicație, spune că tratamentul apare în dosar, dar că aplicația nu poate confirma motivul administrării doar pe baza datelor disponibile. " +
                               "Nu formula diagnostice noi și nu recomanda tratamente.\n" +                               disclaimerRule +
                               "ISTORICUL RECENT AL CONVERSAȚIEI:\n" + (history.isEmpty() ? "Niciun istoric." : history) + "\n\n" +
                               "DATELE PERSONALE ALE UTILIZATORULUI:\n" + userInfo + "\n\n" +
                               "DATE MEDICALE GĂSITE (Pentru întrebarea curentă):\n" + (rows.isEmpty() ? "Nicio informație nouă extrasă." : contextData) + "\n\n" +
                               "ÎNTREBARE NOUĂ: " + message;
            try {
                aiResponse = chatClient.prompt().user(ragPrompt).call().content();
            } catch (Exception e) {
                System.err.println("Eroare la generarea răspunsului AI: " + e.getMessage());
                aiResponse = "Iată ce am găsit în dosarul tău medical în legătură cu căutarea ta.";
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
                    "MATCH (u) WHERE u.id = $id AND (u:Patient OR u:Practitioner) RETURN labels(u)[0] AS role LIMIT 1",
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

    // =====================================================
    // HTML OUTPUT PENTRU "CUM GÂNDEȘTE AI-UL"
    // =====================================================
    private String buildAiThinkingHtml(String prompt) {
        // Evităm erorile de renderizare HTML pentru caractere speciale
        String safePrompt = prompt.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        return """
            <details style="
                background:#f8fafc;
                border-radius:12px;
                padding:12px;
                border: 1px solid #e2e8f0;
                max-width:100%;
                font-family:Arial;
                margin-top:10px;
            ">
            <summary style="
                cursor:pointer;
                color:#475569;
                font-weight:bold;
                font-size:14px;
                outline:none;
                user-select:none;
            ">
                🧠 Cum gândește AI-ul (Prompt & Context)
            </summary>
            <div style="margin-top:12px; font-size:12px; color:#334155; font-family:monospace; background:#f1f5f9; padding:10px; border-radius:8px; overflow-x:auto; max-height:400px; overflow-y:auto;">
                """ + safePrompt + """
            </div>
            </details>
        """;
    }

    // =====================================================
    // UTILS: Calcul Matematic Similaritate Vectorială
    // =====================================================
    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}