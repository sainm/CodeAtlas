package org.sainm.codeatlas.graph.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GraphFactTest {
    @Test
    void factAndEvidenceKeysAreStable() {
        SymbolId source = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
        SymbolId target = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "update", "(Ljava/lang/String;)V");
        FactKey factKey = new FactKey(source, RelationType.CALLS, target, "direct");
        EvidenceKey evidenceKey = new EvidenceKey(SourceType.SPOON, "java-source", "src\\main\\java\\com\\acme\\UserAction.java", 42, 42, "call");

        assertEquals(
            source.value() + "|CALLS|" + target.value() + "|direct",
            factKey.value()
        );
        assertEquals(
            "SPOON|java-source|src/main/java/com/acme/UserAction.java|42|42|call",
            evidenceKey.value()
        );
    }

    @Test
    void tombstoneFactsCannotRemainActive() {
        SymbolId source = SymbolId.logicalPath(SymbolKind.JSP_FORM, "shop", "_root", "src/main/webapp", "user/edit.jsp", "form[0]");
        SymbolId target = SymbolId.logicalPath(SymbolKind.ACTION_PATH, "shop", "_root", "src/main/webapp", "/user/update.do", null);
        FactKey factKey = new FactKey(source, RelationType.SUBMITS_TO, target, "");
        EvidenceKey evidenceKey = new EvidenceKey(SourceType.JASPER, "jsp", "src/main/webapp/user/edit.jsp", 12, 12, "form[0]");

        GraphFact active = GraphFact.active(
            factKey,
            evidenceKey,
            "shop",
            "snapshot-1",
            "run-1",
            "src/main/webapp/user/edit.jsp",
            Confidence.CERTAIN,
            SourceType.JASPER
        );
        GraphFact tombstone = active.tombstone("snapshot-2", "run-2");

        assertTrue(active.active());
        assertFalse(tombstone.active());
        assertTrue(tombstone.tombstone());
        assertThrows(
            IllegalArgumentException.class,
            () -> new GraphFact(factKey, evidenceKey, "shop", "s", "r", "scope", Confidence.CERTAIN, SourceType.JASPER, true, true)
        );
    }

    @Test
    void confidenceAggregationUsesHighestCertainty() {
        assertEquals(Confidence.CERTAIN, Confidence.max(Confidence.POSSIBLE, Confidence.CERTAIN));
        assertEquals(Confidence.LIKELY, Confidence.max(Confidence.LIKELY, Confidence.UNKNOWN));
    }

    @Test
    void aiAssistedFactsCannotBeMarkedCertain() {
        SymbolId source = SymbolId.logicalPath(SymbolKind.CONFIG_KEY, "shop", "_root", "_", "question", null);
        SymbolId target = SymbolId.logicalPath(SymbolKind.CONFIG_KEY, "shop", "_root", "_", "answer", null);
        FactKey factKey = new FactKey(source, RelationType.IMPACTS, target, "ai");
        EvidenceKey evidenceKey = new EvidenceKey(SourceType.AI_ASSISTED, "ai", "prompt", 0, 0, "answer");

        assertThrows(
            IllegalArgumentException.class,
            () -> GraphFact.active(factKey, evidenceKey, "shop", "s1", "r1", "scope", Confidence.CERTAIN, SourceType.AI_ASSISTED)
        );
    }
}
