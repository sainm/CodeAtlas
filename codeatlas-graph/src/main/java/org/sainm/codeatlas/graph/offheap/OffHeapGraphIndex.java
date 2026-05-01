package org.sainm.codeatlas.graph.offheap;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class OffHeapGraphIndex implements AutoCloseable {
    private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
    private final int nodeCount;
    private final int edgeCount;
    private final MemorySegment outgoingOffsets;
    private final MemorySegment outgoingTargets;
    private final MemorySegment outgoingEdgeTypes;
    private final MemorySegment incomingOffsets;
    private final MemorySegment incomingSources;
    private final MemorySegment incomingEdgeTypes;
    private final Arena ownedArena;

    private OffHeapGraphIndex(
        int nodeCount,
        int edgeCount,
        MemorySegment outgoingOffsets,
        MemorySegment outgoingTargets,
        MemorySegment outgoingEdgeTypes,
        MemorySegment incomingOffsets,
        MemorySegment incomingSources,
        MemorySegment incomingEdgeTypes,
        Arena ownedArena
    ) {
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
        this.outgoingOffsets = outgoingOffsets;
        this.outgoingTargets = outgoingTargets;
        this.outgoingEdgeTypes = outgoingEdgeTypes;
        this.incomingOffsets = incomingOffsets;
        this.incomingSources = incomingSources;
        this.incomingEdgeTypes = incomingEdgeTypes;
        this.ownedArena = ownedArena;
    }

    public static OffHeapGraphIndex build(int nodeCount, List<CompressedGraphEdge> edges, Arena arena) {
        return build(nodeCount, edges, arena, null);
    }

    public static OffHeapGraphIndex confined(int nodeCount, List<CompressedGraphEdge> edges) {
        Arena arena = Arena.ofConfined();
        return build(nodeCount, edges, arena, arena);
    }

    public static OffHeapGraphIndex shared(int nodeCount, List<CompressedGraphEdge> edges) {
        Arena arena = Arena.ofShared();
        return build(nodeCount, edges, arena, arena);
    }

    private static OffHeapGraphIndex build(int nodeCount, List<CompressedGraphEdge> edges, Arena arena, Arena ownedArena) {
        if (nodeCount < 0) {
            throw new IllegalArgumentException("nodeCount must be non-negative");
        }
        if (arena == null) {
            throw new IllegalArgumentException("arena is required");
        }
        List<CompressedGraphEdge> normalizedEdges = edges == null ? List.of() : List.copyOf(edges);
        for (CompressedGraphEdge edge : normalizedEdges) {
            if (edge.sourceNodeId() >= nodeCount || edge.targetNodeId() >= nodeCount) {
                throw new IllegalArgumentException("edge node id is outside nodeCount");
            }
        }
        MemorySegment outgoingOffsets = allocateInts(arena, nodeCount + 1);
        MemorySegment outgoingTargets = allocateInts(arena, normalizedEdges.size());
        MemorySegment outgoingEdgeTypes = allocateInts(arena, normalizedEdges.size());
        MemorySegment incomingOffsets = allocateInts(arena, nodeCount + 1);
        MemorySegment incomingSources = allocateInts(arena, normalizedEdges.size());
        MemorySegment incomingEdgeTypes = allocateInts(arena, normalizedEdges.size());

        writeCompressedAdjacency(
            nodeCount,
            normalizedEdges.stream()
                .sorted(Comparator.comparingInt(CompressedGraphEdge::sourceNodeId)
                    .thenComparingInt(CompressedGraphEdge::targetNodeId)
                    .thenComparingInt(CompressedGraphEdge::edgeTypeId))
                .toList(),
            true,
            outgoingOffsets,
            outgoingTargets,
            outgoingEdgeTypes
        );
        writeCompressedAdjacency(
            nodeCount,
            normalizedEdges.stream()
                .sorted(Comparator.comparingInt(CompressedGraphEdge::targetNodeId)
                    .thenComparingInt(CompressedGraphEdge::sourceNodeId)
                    .thenComparingInt(CompressedGraphEdge::edgeTypeId))
                .toList(),
            false,
            incomingOffsets,
            incomingSources,
            incomingEdgeTypes
        );
        return new OffHeapGraphIndex(
            nodeCount,
            normalizedEdges.size(),
            outgoingOffsets,
            outgoingTargets,
            outgoingEdgeTypes,
            incomingOffsets,
            incomingSources,
            incomingEdgeTypes,
            ownedArena
        );
    }

    public int nodeCount() {
        return nodeCount;
    }

    public int edgeCount() {
        return edgeCount;
    }

    public List<Integer> calleesOf(int nodeId) {
        return neighbors(nodeId, outgoingOffsets, outgoingTargets);
    }

    public List<Integer> callersOf(int nodeId) {
        return neighbors(nodeId, incomingOffsets, incomingSources);
    }

    public List<Integer> reachableCallees(int startNodeId, int maxDepth) {
        if (startNodeId < 0 || startNodeId >= nodeCount || maxDepth <= 0) {
            return List.of();
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment visited = arena.allocate(nodeCount);
            MemorySegment frontier = allocateInts(arena, nodeCount);
            MemorySegment nextFrontier = allocateInts(arena, nodeCount);
            List<Integer> result = new ArrayList<>();
            visited.set(ValueLayout.JAVA_BYTE, startNodeId, (byte) 1);
            setInt(frontier, 0, startNodeId);
            int frontierSize = 1;
            for (int depth = 0; depth < maxDepth && frontierSize > 0; depth++) {
                int nextSize = 0;
                for (int i = 0; i < frontierSize; i++) {
                    int current = getInt(frontier, i);
                    int start = getInt(outgoingOffsets, current);
                    int end = getInt(outgoingOffsets, current + 1);
                    for (int edgeIndex = start; edgeIndex < end; edgeIndex++) {
                        int target = getInt(outgoingTargets, edgeIndex);
                        if (visited.get(ValueLayout.JAVA_BYTE, target) == 0) {
                            visited.set(ValueLayout.JAVA_BYTE, target, (byte) 1);
                            setInt(nextFrontier, nextSize++, target);
                            result.add(target);
                        }
                    }
                }
                MemorySegment swap = frontier;
                frontier = nextFrontier;
                nextFrontier = swap;
                frontierSize = nextSize;
            }
            return List.copyOf(result);
        }
    }

    @Override
    public void close() {
        if (ownedArena != null) {
            ownedArena.close();
        }
    }

    private static void writeCompressedAdjacency(
        int nodeCount,
        List<CompressedGraphEdge> edges,
        boolean outgoing,
        MemorySegment offsets,
        MemorySegment targets,
        MemorySegment edgeTypes
    ) {
        int cursor = 0;
        for (int nodeId = 0; nodeId < nodeCount; nodeId++) {
            setInt(offsets, nodeId, cursor);
            while (cursor < edges.size() && owner(edges.get(cursor), outgoing) == nodeId) {
                CompressedGraphEdge edge = edges.get(cursor);
                setInt(targets, cursor, neighbor(edge, outgoing));
                setInt(edgeTypes, cursor, edge.edgeTypeId());
                cursor++;
            }
        }
        setInt(offsets, nodeCount, cursor);
    }

    private List<Integer> neighbors(int nodeId, MemorySegment offsets, MemorySegment values) {
        if (nodeId < 0 || nodeId >= nodeCount) {
            return List.of();
        }
        int start = getInt(offsets, nodeId);
        int end = getInt(offsets, nodeId + 1);
        List<Integer> result = new ArrayList<>(Math.max(0, end - start));
        for (int i = start; i < end; i++) {
            result.add(getInt(values, i));
        }
        return List.copyOf(result);
    }

    private static int owner(CompressedGraphEdge edge, boolean outgoing) {
        return outgoing ? edge.sourceNodeId() : edge.targetNodeId();
    }

    private static int neighbor(CompressedGraphEdge edge, boolean outgoing) {
        return outgoing ? edge.targetNodeId() : edge.sourceNodeId();
    }

    private static MemorySegment allocateInts(Arena arena, int count) {
        return arena.allocate(ValueLayout.JAVA_INT.byteSize() * (long) count, ValueLayout.JAVA_INT.byteAlignment());
    }

    private static void setInt(MemorySegment segment, int index, int value) {
        segment.set(INT, (long) index * INT.byteSize(), value);
    }

    private static int getInt(MemorySegment segment, int index) {
        return segment.get(INT, (long) index * INT.byteSize());
    }
}
