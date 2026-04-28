package com.example.memgraph_api.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.memgraph_api.service.GraphService;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = "*")
public class ProfileController {
    private final GraphService graphService;

    public ProfileController(GraphService graphService) {
        this.graphService = graphService;
    }

    @PostMapping("/update")
    public ResponseEntity<String> updateProfile(@RequestBody Map<String, Object> payload) {

        String identifier = extractIdentifier(payload);

        if (identifier == null) {
            return ResponseEntity.badRequest().body("Missing identifier");
        }

        Object addressObj = payload.get("address");
        String judet = null;
        String localitate = null;

        if (addressObj instanceof List<?>) {
            List<?> list = (List<?>) addressObj;

            if (!list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                Map<?, ?> addr = (Map<?, ?>) list.get(0);

                Object state = addr.get("state");
                Object city = addr.get("city");

                if (state != null) judet = state.toString();
                if (city != null) localitate = city.toString();
            }
        }

        try {
            boolean ok = graphService.updatePatientDomiciliu(identifier, judet, localitate);

            if (ok) {
                return ResponseEntity.ok("Domiciliu actualizat cu succes!");
            } else {
                return ResponseEntity.badRequest().body("Pacient not found.");
            }

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Eroare: " + e.getMessage());
        }
    }

    private String extractIdentifier(Map<String, Object> payload) {
        Object id = payload.get("identifier");

        if (id instanceof List<?>) {
            List<?> list = (List<?>) id;

            if (!list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                Object value = ((Map<?, ?>) list.get(0)).get("value");

                if (value != null) {
                    return value.toString();
                }
            }
        }

        return null;
    }
}
