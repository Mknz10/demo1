package com.example.memgraph_api.controller;

import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final Driver driver;

    public AuthController(Driver driver) {
        this.driver = driver;
    }

    // ================= LOGIN =================
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> payload) {

        String identifier = extractIdentifier(payload);

        if (identifier == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing FHIR identifier"));
        }

        try (Session session = driver.session()) {

            // Changed: Checks for either a Patient OR a Practitioner node
            boolean exists = session.executeRead(tx -> {
                Result result = tx.run(
                        "MATCH (u) " +
                        "WHERE u.id = $identifier AND (u:Patient OR u:Practitioner) " +
                        "RETURN 1 LIMIT 1",
                        Values.parameters("identifier", identifier)
                );
                return result.hasNext();
            });

            if (!exists) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "User not found"));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Database error: " + e.getMessage()));
        }

        return ResponseEntity.ok(Map.of("identifier", identifier));
    }

    // ================= REGISTER =================
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody Map<String, Object> payload) {

        // ✅ enforce FHIR Patient
        if (!"Patient".equals(payload.get("resourceType"))) {
            return ResponseEntity.badRequest()
                    .body("resourceType must be 'Patient'");
        }

        String identifier = extractIdentifier(payload);
        String givenName = extractGivenName(payload);
        String familyName = extractFamilyName(payload);
        String gender = getString(payload, "gender");
        String birthDate = getString(payload, "birthDate");
        String rawPractitionerId = getString(payload, "practitionerId");

        // Fallback to the default Practitioner if the frontend doesn't send it yet
        final String practitionerId = (rawPractitionerId == null || rawPractitionerId.isBlank()) 
                ? "1234567890" 
                : rawPractitionerId.trim();

        if (identifier == null) {
            return ResponseEntity.badRequest().body("Missing identifier");
        }

        try (Session session = driver.session()) {

            boolean exists = session.executeRead(tx -> {
                Result result = tx.run(
                        "MATCH (p:Patient) WHERE p.id = $identifier RETURN 1 LIMIT 1",
                        Values.parameters("identifier", identifier)
                );
                return result.hasNext();
            });

            if (exists) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Patient already exists");
            }

            session.executeWrite(tx -> {
                tx.run(
                    "MERGE (p:Patient {id: $identifier}) " +
                    "SET p.gender = $gender, " +
                    "    p.birthDate = $birthDate, " +
                    "    p.name_use = 'official', " +
                    "    p.name_family = $family, " +
                    "    p.name_given = [$given], " +
                    "    p.name = $given + ' ' + $family, " +
                    "    p.resourceType = 'Patient'",
                    Values.parameters(
                            "identifier", identifier,
                            "gender", gender,
                            "birthDate", birthDate,
                            "given", givenName,
                            "family", familyName
                    )
                ).consume();

                tx.run(
                    "MATCH (pr:Practitioner) WHERE pr.id = $prId " +
                    "MATCH (p:Patient) WHERE p.id = $pid " +
                    "MERGE (pr)-[:TREATS]->(p)",
                    Values.parameters(
                            "prId", practitionerId,
                            "pid", identifier
                    )
                ).consume();

                return null;
            });

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Database error");
        }

        return ResponseEntity.ok("Patient registered (FHIR)");
    }

    // ================= DATA ACCESS TOGGLE =================
    @GetMapping("/access-status")
    public ResponseEntity<Boolean> getAccessStatus(@RequestParam String identifier) {
        try (Session session = driver.session()) {
            boolean hasAccess = session.executeRead(tx -> {
                Result result = tx.run(
                        "MATCH (:Practitioner)-[:TREATS]->(p:Patient) WHERE p.id = $pid RETURN count(p) > 0 AS hasAccess",
                        Values.parameters("pid", identifier)
                );
                return result.hasNext() && result.next().get("hasAccess").asBoolean();
            });
            return ResponseEntity.ok(hasAccess);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(false);
        }
    }

    @PostMapping("/toggle-access")
    public ResponseEntity<String> toggleAccess(@RequestBody Map<String, Object> payload) {
        String patientId = getString(payload, "identifier");
        
        Object enableObj = payload.get("enable");
        Boolean enable = (enableObj instanceof Boolean) ? (Boolean) enableObj 
                       : (enableObj != null ? Boolean.parseBoolean(enableObj.toString()) : null);

        if (patientId == null || enable == null) return ResponseEntity.badRequest().body("Missing parameters");

        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                if (enable) {
                tx.run("MATCH (p:Patient) WHERE p.id = $pid " +
                           "MATCH (pr:Practitioner {id: coalesce(p.practitionerId, '1234567890')}) " +
                           "MERGE (pr)-[:TREATS]->(p)", Values.parameters("pid", patientId)).consume();
                } else {
                tx.run("MATCH (pr:Practitioner)-[r:TREATS]->(p:Patient) WHERE p.id = $pid " +
                           "SET p.practitionerId = pr.id " +
                           "DELETE r", Values.parameters("pid", patientId)).consume();
                }
                return null;
            });
            return ResponseEntity.ok(enable ? "Access enabled" : "Access disabled");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Database error");
        }
    }

    // ================= FHIR HELPERS =================

    private String extractIdentifier(Map<String, Object> payload) {
        Object id = payload.get("identifier");

        if (id instanceof List<?>) {
            List<?> list = (List<?>) id;

            if (!list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                Object value = ((Map<?, ?>) list.get(0)).get("value");

                if (value instanceof String) {
                    return (String) value;
                }
            }
        }
        return null;
    }

    private String extractGivenName(Map<String, Object> payload) {
        Object nameObj = payload.get("name");

        if (nameObj instanceof List<?>) {
            List<?> list = (List<?>) nameObj;

            if (!list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                Object given = ((Map<?, ?>) list.get(0)).get("given");

                if (given instanceof List<?>) {
                    List<?> givenList = (List<?>) given;

                    if (!givenList.isEmpty()) {
                        return String.valueOf(givenList.get(0));
                    }
                }
            }
        }
        return null;
    }

    private String extractFamilyName(Map<String, Object> payload) {
        Object nameObj = payload.get("name");

        if (nameObj instanceof List<?>) {
            List<?> list = (List<?>) nameObj;

            if (!list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                Object family = ((Map<?, ?>) list.get(0)).get("family");

                if (family != null) {
                    return String.valueOf(family);
                }
            }
        }
        return null;
    }

    private String getString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? String.valueOf(value) : null;
    }
}