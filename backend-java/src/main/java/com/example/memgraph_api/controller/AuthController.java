package com.example.memgraph_api.controller;

import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final Driver driver;

    public AuthController(Driver driver) {
        this.driver = driver;
    }

    // ================= LOGIN =================
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody Map<String, Object> payload) {

        String identifier = extractIdentifier(payload);

        if (identifier == null) {
            return ResponseEntity.badRequest()
                    .body("Missing FHIR identifier");
        }

        try (Session session = driver.session()) {

            boolean exists = session.executeRead(tx -> {
                Result result = tx.run(
                        "MATCH (p:Patient {identifier: $identifier}) RETURN 1 LIMIT 1",
                        Values.parameters("identifier", identifier)
                );
                return result.hasNext();
            });

            if (!exists) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Patient not found");
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Database error");
        }

        return ResponseEntity.ok(identifier);
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

        if (identifier == null) {
            return ResponseEntity.badRequest().body("Missing identifier");
        }

        try (Session session = driver.session()) {

            boolean exists = session.executeRead(tx -> {
                Result result = tx.run(
                        "MATCH (p:Patient {identifier: $identifier}) RETURN 1 LIMIT 1",
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
                    "MERGE (p:Patient {identifier: $identifier}) " +
                    "SET p.gender = $gender, " +
                    "    p.birthDate = $birthDate, " +
                    "    p.name = [{ " +
                    "        use: 'official', " +
                    "        family: $family, " +
                    "        given: [$given] " +
                    "    }]",
                    Values.parameters(
                            "identifier", identifier,
                            "given", givenName,
                            "family", familyName,
                            "gender", gender,
                            "birthDate", birthDate
                    )
                );
                return null;
            });

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Database error");
        }

        return ResponseEntity.ok("Patient registered (FHIR)");
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