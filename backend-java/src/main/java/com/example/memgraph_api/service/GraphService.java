package com.example.memgraph_api.service;

import com.example.memgraph_api.model.Edge;
import com.example.memgraph_api.model.Node;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GraphService {

    // IMPORTANT: Memgraph uses the Bolt protocol on port 7687
    private final Driver driver = GraphDatabase.driver(
            "bolt://localhost:7687",
            AuthTokens.none() // Memgraph usually doesn't require auth by default
    );

    /**
     * Executes a Cypher query and transforms the results into a standardized format
     * expected by the React frontend (list of Nodes and list of Edges).
     */
    public Map<String, List<?>> getGraphData() {
        // The standard query to get a slice of the graph
        String cypherQuery = "MATCH (n)-[r]-(m) RETURN n, r, m LIMIT 25";

        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();

        // Use a Set or Map to track unique nodes and edges to avoid duplicates
        Map<String, Node> uniqueNodes = new HashMap<>();
        Map<String, Edge> uniqueEdges = new HashMap<>();

        try (Session session = driver.session()) {
            Result result = session.run(cypherQuery);

            for (Record record : result.list()) {
                // 1. Extract Nodes (n and m)
                for (String key : List.of("n", "m")) {
                    if (record.containsKey(key)) {
                        org.neo4j.driver.types.Node neo4jNode = record.get(key).asNode();
                        String nodeId = String.valueOf(neo4jNode.id());

                        if (!uniqueNodes.containsKey(nodeId)) {
                            // Extract properties, label, and a display name
                            Map<String, Object> props = neo4jNode.asMap();
                            String label = neo4jNode.labels().iterator().next(); // Get the primary label
                            String name = props.containsKey("name") ? (String) props.get("name") : label + " " + nodeId;
                            String color = label.equals("Person") ? "bg-indigo-500" : "bg-rose-500"; // Simple color mapping

                            Node node = new Node(nodeId, label, name, props, color);
                            uniqueNodes.put(nodeId, node);
                        }
                    }
                }

                // 2. Extract Edges (r)
                if (record.containsKey("r")) {
                    org.neo4j.driver.types.Relationship neo4jRel = record.get("r").asRelationship();
                    String edgeId = String.valueOf(neo4jRel.id());

                    if (!uniqueEdges.containsKey(edgeId)) {
                        // Create the Edge object
                        Edge edge = new Edge(
                            edgeId,
                            String.valueOf(neo4jRel.startNodeId()),
                            String.valueOf(neo4jRel.endNodeId()),
                            neo4jRel.type(),
                            neo4jRel.asMap()
                        );
                        uniqueEdges.put(edgeId, edge);
                    }
                }
            }

            // Convert the Maps back to Lists for the final JSON output
            nodes.addAll(uniqueNodes.values());
            edges.addAll(uniqueEdges.values());

        } catch (Exception e) {
            System.err.println("Error running Cypher query: " + e.getMessage());
            // Return empty structure on error
            return Map.of("nodes", List.of(), "edges", List.of());
        }

        return Map.of("nodes", nodes, "edges", edges);
    }
}