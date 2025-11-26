package com.example.memgraph_api.controller;

import com.example.memgraph_api.service.GraphService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/graph")
// Crucial for React: allows the React frontend (e.g., on port 5173 or 3000)
// to make requests to the Java backend (on port 8080)
@CrossOrigin(origins = "*")
public class GraphController {

    private final GraphService graphService;

    // Dependency injection via constructor
    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    /**
     * GET /api/graph
     * Returns the graph data (nodes and edges) from Memgraph.
     */
    @GetMapping
    public ResponseEntity<Map<String, List<?>>> getGraphData() {
        Map<String, List<?>> data = graphService.getGraphData();
        return ResponseEntity.ok(data);
    }
}