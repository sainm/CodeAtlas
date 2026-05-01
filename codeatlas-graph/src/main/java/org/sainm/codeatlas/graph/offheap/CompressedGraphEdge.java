package org.sainm.codeatlas.graph.offheap;

public record CompressedGraphEdge(
    int sourceNodeId,
    int targetNodeId,
    int edgeTypeId
) {
    public CompressedGraphEdge {
        if (sourceNodeId < 0) {
            throw new IllegalArgumentException("sourceNodeId must be non-negative");
        }
        if (targetNodeId < 0) {
            throw new IllegalArgumentException("targetNodeId must be non-negative");
        }
        if (edgeTypeId < 0) {
            throw new IllegalArgumentException("edgeTypeId must be non-negative");
        }
    }
}
