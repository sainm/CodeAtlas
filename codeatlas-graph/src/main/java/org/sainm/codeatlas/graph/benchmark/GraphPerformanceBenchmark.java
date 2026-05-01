package org.sainm.codeatlas.graph.benchmark;

import org.sainm.codeatlas.graph.cache.AdjacencyCacheKey;
import org.sainm.codeatlas.graph.cache.PrimitiveAdjacencyCache;
import org.sainm.codeatlas.graph.cache.RelationGroup;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.neo4j.Neo4jGraphQueryBuilder;
import org.sainm.codeatlas.graph.offheap.CompressedGraphEdge;
import org.sainm.codeatlas.graph.offheap.OffHeapGraphIndex;
import org.sainm.codeatlas.graph.store.ActiveFact;
import java.util.List;

public final class GraphPerformanceBenchmark {
    private final BenchmarkTimer timer = new BenchmarkTimer();
    private final Neo4jGraphQueryBuilder neo4jQueryBuilder = new Neo4jGraphQueryBuilder();

    public BenchmarkResult measureNeo4jCallerQueryTemplate(
        String projectId,
        String snapshotId,
        String symbolId,
        int iterations
    ) {
        return timer.measure(
            "neo4j-caller-query-template",
            Math.min(10, iterations),
            iterations,
            () -> neo4jQueryBuilder.findCallers(projectId, snapshotId, symbolId, 50),
            0
        );
    }

    public BenchmarkResult measureAdjacencyCacheQueries(
        String projectId,
        String snapshotId,
        List<ActiveFact> activeFacts,
        SymbolId start,
        int iterations
    ) {
        PrimitiveAdjacencyCache cache = PrimitiveAdjacencyCache.build(
            new AdjacencyCacheKey(projectId, snapshotId, RelationGroup.CALL),
            activeFacts
        );
        return timer.measure(
            "jvm-primitive-adjacency-cache",
            Math.min(10, iterations),
            iterations,
            () -> {
                cache.callees(start);
                cache.bfs(start, 4, 50);
            },
            cache.stats().estimatedHeapBytes()
        );
    }

    public FfmActivationDecision evaluateFfmActivation(
        long edgeCount,
        BenchmarkResult benchmarkResult,
        FfmActivationPolicy policy
    ) {
        if (benchmarkResult == null) {
            throw new IllegalArgumentException("benchmarkResult is required");
        }
        FfmActivationPolicy safePolicy = policy == null ? FfmActivationPolicy.defaultPolicy() : policy;
        return safePolicy.evaluate(new GraphScaleMetrics(
            edgeCount,
            benchmarkResult.p95Millis(),
            benchmarkResult.estimatedHeapBytes()
        ));
    }

    public BenchmarkResult measureOffHeapGraphIndexQueries(
        int nodeCount,
        List<CompressedGraphEdge> edges,
        int startNodeId,
        int iterations
    ) {
        try (OffHeapGraphIndex index = OffHeapGraphIndex.confined(nodeCount, edges)) {
            return timer.measure(
                "ffm-offheap-graph-index",
                Math.min(10, iterations),
                iterations,
                () -> {
                    index.calleesOf(startNodeId);
                    index.callersOf(startNodeId);
                    index.reachableCallees(startNodeId, 4);
                },
                0L
            );
        }
    }
}
