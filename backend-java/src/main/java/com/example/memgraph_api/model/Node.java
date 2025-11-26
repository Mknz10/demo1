package com.example.memgraph_api.model;

import java.util.Map;

/**
 * Immutable representation of a graph node exposed to the React client.
 */
public record Node(
        String id,
        String label,
        String name,
        Map<String, Object> properties,
        String color
) {}