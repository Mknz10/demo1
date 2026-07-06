package com.example.memgraph_api.controller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class DocumentController {

    private final Driver driver;

    public DocumentController(Driver driver) {
        this.driver = driver;
    }

    @GetMapping("/practitioner/{identifier}")
    public ResponseEntity<?> getPractitionerDocuments(@PathVariable String identifier) {
        String cypher = "MATCH (pr:Practitioner)-[:TREATS]->(p:Patient)-[]->(n)-[:source]->(d:Document) WHERE pr.id = $id " +
                        "AND d.name IS NOT NULL " +
                        "RETURN DISTINCT d.id AS id, d.name AS name, p.id AS patientId, " +
                        "p.name_given AS patientGiven, p.name_family AS patientFamily, d.uploadDate AS uploadDate " +
                        "ORDER BY d.uploadDate DESC";
        List<Map<String, Object>> docs = new ArrayList<>();
        try (Session session = driver.session()) {
            Result result = session.run(cypher, Values.parameters("id", identifier));
            while (result.hasNext()) {
                docs.add(result.next().asMap());
            }
            return ResponseEntity.ok(docs);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/patients/{identifier}")
    public ResponseEntity<?> getPatients(@PathVariable String identifier) {
        String cypher = "MATCH (pr:Practitioner)-[:TREATS]->(p:Patient) WHERE pr.id = $id " +
                        "RETURN p.id AS id, p.gender AS gender, p.birthDate AS birthDate ";
        List<Map<String, Object>> patients = new ArrayList<>();
        try (Session session = driver.session()) {
            Result result = session.run(cypher, Values.parameters("id", identifier));
            while (result.hasNext()) {
                patients.add(result.next().asMap());
            }
            return ResponseEntity.ok(patients);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/patient/{identifier}")
    public ResponseEntity<?> getPatientDocuments(@PathVariable String identifier) {
        String cypher = "MATCH (p:Patient)-[]->(n)-[:source]->(d:Document) WHERE p.id = $id " +
                        "AND d.name IS NOT NULL " +
                        "RETURN DISTINCT d.id AS id, d.name AS name, d.uploadDate AS uploadDate " +
                        "ORDER BY d.uploadDate DESC LIMIT 50";
        List<Map<String, Object>> docs = new ArrayList<>();
        try (Session session = driver.session()) {
            Result result = session.run(cypher, Values.parameters("id", identifier));
            while (result.hasNext()) {
                docs.add(result.next().asMap());
            }
            return ResponseEntity.ok(docs);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/download/{id:.+}")
    public ResponseEntity<Resource> downloadDocument(@PathVariable String id) {
        String cypher = "MATCH (d:Document {id: $id}) RETURN d.name AS name";
        String fileName = null;
        
        try (Session session = driver.session()) {
            Result result = session.run(cypher, Values.parameters("id", id));
            if (result.hasNext()) {
                fileName = result.next().get("name").asString();
            }
        }
        
        if (fileName == null) {
            System.err.println("Document not found in database for ID: " + id);
            return ResponseEntity.notFound().build();
        }

        try {
            Path filePath = Paths.get("uploads").resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(resource);
            } else {
                System.err.println("File not found on disk: " + filePath.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}