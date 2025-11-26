package com.example.memgraph_api.model;

import java.util.Map;

/**
 * Immutable representation of a graph edge exposed to the React client.
 */
public record Edge(
        String id,
        String source,
        String target,
        String label,
        Map<String, Object> properties
) {}