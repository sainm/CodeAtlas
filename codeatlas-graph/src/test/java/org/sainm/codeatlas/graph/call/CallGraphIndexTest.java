package org.sainm.codeatlas.graph.call;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.store.InMemoryGraphRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class CallGraphIndexTest {
    @Test
    void buildsCallerAndCalleeIndexesFromActiveCallFacts() {
        SymbolId action = method("com.acme.UserAction", "execute");
        SymbolId service = method("com.acme.UserService", "save");
        SymbolId mapper = method("com.acme.UserMapper", "insert");
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(action, service));
        repository.upsertFact(active(service, mapper));

        CallGraphIndex index = CallGraphIndex.fromActiveFacts(repository.activeFacts("shop", "snapshot-1"));

        assertEquals(2, index.edgeCount());
        assertEquals(List.of(service), index.callers(mapper).stream().map(CallEdge::caller).toList());
        assertEquals(List.of(service), index.callees(action).stream().map(CallEdge::callee).toList());
    }

    private GraphFact active(SymbolId caller, SymbolId callee) {
        return GraphFact.active(
            new FactKey(caller, RelationType.CALLS, callee, "direct"),
            new EvidenceKey(SourceType.SPOON, "test", "UserService.java", 10, 10, "call"),
            "shop",
            "snapshot-1",
            "run-1",
            "scope-java",
            Confidence.CERTAIN,
            SourceType.SPOON
        );
    }

    private SymbolId method(String owner, String name) {
        return SymbolId.method("shop", "_root", "src/main/java", owner, name, "()V");
    }
}
