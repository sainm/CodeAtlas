package org.sainm.codeatlas.graph.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.offheap.CompressedGraphEdge;
import org.sainm.codeatlas.graph.store.InMemoryGraphRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphPerformanceBenchmarkTest {
    @Test
    void recordsNeo4jQueryTemplateP95() {
        BenchmarkResult result = new GraphPerformanceBenchmark().measureNeo4jCallerQueryTemplate(
            "shop",
            "snapshot-1",
            method("Target", "execute").value(),
            20
        );

        assertEquals("neo4j-caller-query-template", result.name());
        assertEquals(20, result.iterations());
        assertTrue(result.p95Millis() >= 0.0);
        assertTrue(result.averageMillis() >= 0.0);
    }

    @Test
    void recordsPrimitiveAdjacencyCacheP95AndHeapEstimate() {
        SymbolId first = method("A", "a");
        SymbolId second = method("B", "b");
        SymbolId third = method("C", "c");
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(first, second));
        repository.upsertFact(active(second, third));

        BenchmarkResult result = new GraphPerformanceBenchmark().measureAdjacencyCacheQueries(
            "shop",
            "snapshot-1",
            repository.activeFacts("shop", "snapshot-1"),
            first,
            20
        );

        assertEquals("jvm-primitive-adjacency-cache", result.name());
        assertEquals(20, result.iterations());
        assertTrue(result.p95Millis() >= 0.0);
        assertTrue(result.estimatedHeapBytes() > 0L);
    }

    @Test
    void evaluatesFfmActivationFromBenchmarkResult() {
        GraphPerformanceBenchmark benchmark = new GraphPerformanceBenchmark();
        FfmActivationPolicy policy = new FfmActivationPolicy(10, 5.0d, 1_024L);
        BenchmarkResult result = new BenchmarkResult("synthetic", 20, 8.0d, 3.0d, 1_000L, 2_048L);

        FfmActivationDecision decision = benchmark.evaluateFfmActivation(10, result, policy);

        assertTrue(decision.recommended());
    }

    @Test
    void recordsOffHeapGraphIndexP95AndHeapEstimate() {
        BenchmarkResult result = new GraphPerformanceBenchmark().measureOffHeapGraphIndexQueries(
            4,
            List.of(
                new CompressedGraphEdge(0, 1, 10),
                new CompressedGraphEdge(1, 2, 10),
                new CompressedGraphEdge(2, 3, 10)
            ),
            0,
            20
        );

        assertEquals("ffm-offheap-graph-index", result.name());
        assertEquals(20, result.iterations());
        assertTrue(result.p95Millis() >= 0.0);
        assertEquals(0L, result.estimatedHeapBytes());
    }

    private GraphFact active(SymbolId source, SymbolId target) {
        return GraphFact.active(
            new FactKey(source, RelationType.CALLS, target, "direct"),
            new EvidenceKey(SourceType.SPOON, "benchmark-test", "A.java", 1, 1, "call"),
            "shop",
            "snapshot-1",
            "run-1",
            "scope",
            Confidence.CERTAIN,
            SourceType.SPOON
        );
    }

    private SymbolId method(String owner, String name) {
        return SymbolId.method("shop", "_root", "src/main/java", "com.acme." + owner, name, "()V");
    }
}
