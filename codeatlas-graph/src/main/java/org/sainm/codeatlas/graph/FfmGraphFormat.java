package org.sainm.codeatlas.graph;

import java.util.List;

/**
 * Design contract for FFM (Foreign Function &amp; Memory) CSR/CSC graph format.
 *
 * <p>The FFM graph uses compressed sparse row (CSR) and compressed sparse column (CSC)
 * layouts mapped to {@link java.lang.foreign.MemorySegment}. This enables zero-copy
 * mmap-based read access for high-performance caller/callee and bounded BFS queries.
 *
 * <p>The graph is stored as a single memory-mapped file with four regions:
 * <ol>
 *   <li>Header — version, node count, edge count, offsets</li>
 *   <li>Node table — identity ID offsets and lengths</li>
 *   <li>CSR index — edge offsets per source node</li>
 *   <li>CSC index — edge offsets per target node</li>
 *   <li>Edge table — target node index, edge type ordinal</li>
 *   <li>String pool — packed UTF-8 identity ID strings</li>
 * </ol>
 */
public final class FfmGraphFormat {
    private FfmGraphFormat() {
    }

    public static final int VERSION = 1;
    public static final int HEADER_BYTES = 64;

    /** Maximum node count supported by the format. */
    public static final int MAX_NODES = 10_000_000;

    /** Maximum edge count supported by the format. */
    public static final int MAX_EDGES = 100_000_000;

    public enum EdgeType {
        CALLS(0),
        INVOKES(1),
        READS_TABLE(2),
        WRITES_TABLE(3),
        BINDS_TO(4),
        ROUTES_TO(5),
        HAS_NATIVE_BOUNDARY(6),
        CALLS_NATIVE(7),
        PASSES_PARAM(8),
        READS_COLUMN(9),
        WRITES_COLUMN(10);

        private final int edgeCode;

        EdgeType(int edgeCode) {
            this.edgeCode = edgeCode;
        }

        public int edgeCode() {
            return edgeCode;
        }
    }

    /**
     * Header layout within the mmap file.
     *
     * @param version           format version (1)
     * @param nodeCount         total number of nodes
     * @param edgeCount         total number of edges
     * @param nodeTableOffset   byte offset to node table
     * @param csrOffset         byte offset to CSR index
     * @param cscOffset         byte offset to CSC index
     * @param edgeTableOffset   byte offset to edge table
     * @param stringPoolOffset  byte offset to string pool
     */
    public record Header(
            int version,
            int nodeCount,
            int edgeCount,
            long nodeTableOffset,
            long csrOffset,
            long cscOffset,
            long edgeTableOffset,
            long stringPoolOffset) {
        public static Header initial() {
            return new Header(VERSION, 0, 0,
                    HEADER_BYTES, 0, 0, 0, 0);
        }
    }

    /**
     * CSR (Compressed Sparse Row) entry: start offset and count of edges for a source node.
     */
    public record CsrEntry(long edgeStart, int edgeCount) {
    }

    /**
     * CSC (Compressed Sparse Column) entry: start offset and count of edges for a target node.
     */
    public record CscEntry(long edgeStart, int edgeCount) {
    }

    /**
     * A single edge in the graph.
     */
    public record Edge(int targetIndex, int edgeTypeOrdinal) {
    }

    /**
     * A node entry pointing into the string pool.
     */
    public record NodeEntry(long stringPoolOffset, int stringLength) {
    }

    /**
     * Activation policy for FFM routing.
     * Only routes to FFM when a named benchmark profile activates it.
     */
    public record ActivationPolicy(
            String requiredBenchmarkProfileName,
            boolean fallbackToJvmCache) {
        public ActivationPolicy {
            if (requiredBenchmarkProfileName == null || requiredBenchmarkProfileName.isBlank()) {
                throw new IllegalArgumentException("requiredBenchmarkProfileName is required");
            }
        }
    }
}
