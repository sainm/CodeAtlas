package org.sainm.codeatlas.graph.offheap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.util.List;
import org.junit.jupiter.api.Test;

class OffHeapCsrGraphIndexTest {
    @Test
    void buildsCsrAndCscSegmentsForCallerCalleeQueries() {
        try (Arena arena = Arena.ofConfined()) {
            OffHeapGraphIndex index = OffHeapGraphIndex.build(
                4,
                List.of(
                    new CompressedGraphEdge(0, 1, 10),
                    new CompressedGraphEdge(0, 2, 10),
                    new CompressedGraphEdge(2, 3, 20)
                ),
                arena
            );

            assertEquals(List.of(1, 2), index.calleesOf(0));
            assertEquals(List.of(0), index.callersOf(1));
            assertEquals(List.of(0), index.callersOf(2));
            assertEquals(List.of(3), index.calleesOf(2));
            assertEquals(List.of(), index.calleesOf(3));
            assertEquals(4, index.nodeCount());
            assertEquals(3, index.edgeCount());
        }
    }

    @Test
    void validatesCompressedNodeIdsAndEdgeTypes() {
        assertThrows(IllegalArgumentException.class, () -> new CompressedGraphEdge(-1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CompressedGraphEdge(0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CompressedGraphEdge(0, 1, -1));
    }

    @Test
    void canOwnConfinedAndSharedArenas() {
        try (OffHeapGraphIndex confined = OffHeapGraphIndex.confined(
            2,
            List.of(new CompressedGraphEdge(0, 1, 10))
        )) {
            assertEquals(List.of(1), confined.calleesOf(0));
        }
        try (OffHeapGraphIndex shared = OffHeapGraphIndex.shared(
            2,
            List.of(new CompressedGraphEdge(0, 1, 10))
        )) {
            assertEquals(List.of(0), shared.callersOf(1));
        }
    }

    @Test
    void findsReachableCalleesWithBoundedBfs() {
        try (OffHeapGraphIndex index = OffHeapGraphIndex.confined(
            5,
            List.of(
                new CompressedGraphEdge(0, 1, 10),
                new CompressedGraphEdge(1, 2, 10),
                new CompressedGraphEdge(2, 0, 10),
                new CompressedGraphEdge(2, 3, 10),
                new CompressedGraphEdge(3, 4, 10)
            )
        )) {
            assertEquals(List.of(1, 2), index.reachableCallees(0, 2));
            assertEquals(List.of(1, 2, 3, 4), index.reachableCallees(0, 4));
            assertEquals(List.of(), index.reachableCallees(0, 0));
        }
    }
}
