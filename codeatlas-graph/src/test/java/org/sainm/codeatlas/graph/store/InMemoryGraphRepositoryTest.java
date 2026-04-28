package org.sainm.codeatlas.graph.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InMemoryGraphRepositoryTest {
    private final SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
    private final SymbolId service = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "update", "(Ljava/lang/String;)V");
    private final FactKey call = new FactKey(action, RelationType.CALLS, service, "direct");

    @Test
    void upsertNodeMergesRolesAndPropertiesWithoutDuplicatingCodeNodes() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        GraphNode classNode = new GraphNode(
            SymbolId.classSymbol("shop", "_root", "src/main/java", "com.acme.UserAction"),
            Set.of(NodeRole.CODE_TYPE),
            Map.of("name", "UserAction")
        );
        GraphNode controllerRole = new GraphNode(
            classNode.symbolId(),
            Set.of(NodeRole.STRUTS_ACTION),
            Map.of("framework", "struts1")
        );

        repository.upsertNode(classNode);
        GraphNode merged = repository.upsertNode(controllerRole);

        assertEquals(Set.of(NodeRole.CODE_TYPE, NodeRole.STRUTS_ACTION), merged.roles());
        assertEquals("UserAction", merged.properties().get("name"));
        assertEquals("struts1", merged.properties().get("framework"));
        assertEquals(merged, repository.findNode(classNode.symbolId()).orElseThrow());
    }

    @Test
    void activeFactsAggregateMultipleEvidenceItemsAndConfidence() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(activeFact("snapshot-1", "run-1", "scope-a", evidence("UserAction.java", 10), Confidence.CERTAIN, SourceType.SPOON));
        repository.upsertFact(activeFact("snapshot-1", "run-1", "scope-b", evidence("struts-config.xml", 22), Confidence.LIKELY, SourceType.XML));

        ActiveFact activeFact = repository.activeFact("shop", "snapshot-1", call).orElseThrow();

        assertEquals(2, activeFact.evidenceKeys().size());
        assertEquals(Confidence.CERTAIN, activeFact.confidence());
        assertEquals(Set.of(SourceType.SPOON, SourceType.XML), activeFact.sourceTypes());
    }

    @Test
    void reanalyzeScopeTombstonesOnlyMissingEvidenceFromThatScope() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        GraphFact spoonEvidence = activeFact("snapshot-1", "run-1", "scope-java", evidence("UserAction.java", 10), Confidence.CERTAIN, SourceType.SPOON);
        GraphFact xmlEvidence = activeFact("snapshot-1", "run-1", "scope-xml", evidence("struts-config.xml", 22), Confidence.LIKELY, SourceType.XML);
        repository.upsertFact(spoonEvidence);
        repository.upsertFact(xmlEvidence);

        repository.reanalyzeScope(
            "shop",
            "snapshot-1",
            "snapshot-2",
            "run-2",
            "scope-java",
            List.of()
        );

        ActiveFact stillActive = repository.activeFact("shop", "snapshot-2", call).orElseThrow();
        assertEquals(1, stillActive.evidenceKeys().size());
        assertEquals(SourceType.XML, stillActive.sourceTypes().iterator().next());

        SnapshotDiff diff = repository.diff("shop", "snapshot-1", "snapshot-2");
        assertFalse(diff.added().contains(call));
        assertFalse(diff.removed().contains(call));
        assertTrue(diff.retained().contains(call));
    }

    @Test
    void diffDetectsRemovedFactsWhenLastEvidenceIsTombstoned() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(activeFact("snapshot-1", "run-1", "scope-java", evidence("UserAction.java", 10), Confidence.CERTAIN, SourceType.SPOON));

        repository.reanalyzeScope(
            "shop",
            "snapshot-1",
            "snapshot-2",
            "run-2",
            "scope-java",
            List.of()
        );

        SnapshotDiff diff = repository.diff("shop", "snapshot-1", "snapshot-2");
        assertTrue(diff.removed().contains(call));
        assertTrue(repository.activeFacts("shop", "snapshot-2").isEmpty());
    }

    @Test
    void sameEvidenceKeyCanSupportMultipleFacts() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        SymbolId mapper = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserMapper", "update", "()V");
        FactKey secondCall = new FactKey(service, RelationType.CALLS, mapper, "direct");
        EvidenceKey sharedEvidence = evidence("UserService.java", 14);

        repository.upsertFact(GraphFact.active(call, sharedEvidence, "shop", "snapshot-1", "run-1", "scope-java", Confidence.CERTAIN, SourceType.SPOON));
        repository.upsertFact(GraphFact.active(secondCall, sharedEvidence, "shop", "snapshot-1", "run-1", "scope-java", Confidence.CERTAIN, SourceType.SPOON));

        assertEquals(2, repository.activeFacts("shop", "snapshot-1").size());
    }

    private GraphFact activeFact(
        String snapshotId,
        String analysisRunId,
        String scopeKey,
        EvidenceKey evidenceKey,
        Confidence confidence,
        SourceType sourceType
    ) {
        return GraphFact.active(call, evidenceKey, "shop", snapshotId, analysisRunId, scopeKey, confidence, sourceType);
    }

    private EvidenceKey evidence(String path, int line) {
        SourceType sourceType = path.endsWith(".xml") ? SourceType.XML : SourceType.SPOON;
        return new EvidenceKey(sourceType, "test", path, line, line, "call");
    }
}
