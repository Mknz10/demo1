package com.example.memgraph_api.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import com.example.memgraph_api.model.Edge;
import com.example.memgraph_api.model.Node;

@Service
public class GraphService {

    private final Driver driver = GraphDatabase.driver(
            "bolt://localhost:7687",
            AuthTokens.none()
    );

    // ================= UPDATE PATIENT =================
    public boolean updatePatientDomiciliu(String identifier, String judet, String localitate) {

        String cypher =
                "MATCH (p:Patient {identifier: $identifier}) " +
                "SET p.judet = $judet, p.localitate = $localitate " +
                "RETURN p";

        try (Session session = driver.session()) {
            Result result = session.run(cypher, Map.of(
                    "identifier", identifier,
                    "judet", judet,
                    "localitate", localitate
            ));
            return result.hasNext();
        } catch (Exception e) {
            System.err.println("Error updating domiciliu: " + e.getMessage());
            return false;
        }
    }

    // ================= GRAPH FETCH =================
    public Map<String, List<?>> getGraphData(String identifier) {

                String cypherQuery =
                             (identifier != null && !identifier.isEmpty())
                                                ? "MATCH (p:Patient {identifier: $identifier}) " +
                                                    "OPTIONAL MATCH (n)-[r:subject]->(p) " +
                                                    "WHERE n:Condition OR n:Observation OR n:MedicationRequest OR n:Procedure " +
                                                    "RETURN p AS n, r, n AS m LIMIT 500"
                                                : "MATCH (n) WHERE n:Patient OR n:Condition OR n:Observation OR n:MedicationRequest OR n:Procedure " +
                                                    "OPTIONAL MATCH (n)-[r]->(m) " +
                                                    "WHERE m:Patient OR m:Condition OR m:Observation OR m:MedicationRequest OR m:Procedure " +
                                                    "RETURN n, r, m LIMIT 500";

        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();

        Map<String, Node> uniqueNodes = new HashMap<>();
        Map<String, Edge> uniqueEdges = new HashMap<>();

        try (Session session = driver.session()) {

            Result result = session.run(
                    cypherQuery,
                    identifier != null && !identifier.isEmpty()
                            ? Map.of("identifier", identifier)
                            : Map.of()
            );

            for (Record record : result.list()) {

                // ================= NODES =================
                for (String key : List.of("n", "m")) {

                    if (record.containsKey(key) && !record.get(key).isNull()) {

                        var neoNode = record.get(key).asNode();
                        String nodeId = String.valueOf(neoNode.id());

                        if (!uniqueNodes.containsKey(nodeId)) {

                            Map<String, Object> props = neoNode.asMap();

                            String label = neoNode.labels().iterator().hasNext()
                                    ? neoNode.labels().iterator().next()
                                    : "Node";

                            // ================= FHIR NAME (NO FLATTENING) =================
                            Object g = props.get("given");
                            Object f = props.get("family");

                            String given = g != null ? g.toString() : "";
                            String family = f != null ? f.toString() : "";

                            String displayName;

                            if (!given.isBlank() || !family.isBlank()) {
                                displayName = (given + " " + family).trim();
                            } else {
                                displayName = label + " " + nodeId;
                            }

                            Node node = new Node(nodeId, label, displayName, props);
                            uniqueNodes.put(nodeId, node);
                        }
                    }
                }

                // ================= EDGES =================
                if (record.containsKey("r") && !record.get("r").isNull()) {

                    var rel = record.get("r").asRelationship();
                    String edgeId = String.valueOf(rel.id());

                    if (!uniqueEdges.containsKey(edgeId)) {

                        Edge edge = new Edge(
                                edgeId,
                                String.valueOf(rel.startNodeId()),
                                String.valueOf(rel.endNodeId()),
                                rel.type(),
                                rel.asMap()
                        );

                        uniqueEdges.put(edgeId, edge);
                    }
                }
            }

            nodes.addAll(uniqueNodes.values());
            edges.addAll(uniqueEdges.values());

        } catch (Exception e) {
            System.err.println("Error running Cypher query: " + e.getMessage());
            return Map.of("nodes", List.of(), "edges", List.of());
        }

        return Map.of("nodes", nodes, "edges", edges);
    }
}