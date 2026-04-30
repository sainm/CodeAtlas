package org.sainm.codeatlas.graph.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import org.sainm.codeatlas.graph.store.InMemoryGraphRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphFlowQueryEngineTest {
    private final SymbolId jsp = SymbolId.logicalPath(SymbolKind.JSP_PAGE, "shop", "_root", "src/main/webapp", "user/edit.jsp", null);
    private final SymbolId actionPath = SymbolId.logicalPath(SymbolKind.ACTION_PATH, "shop", "_root", "src/main/webapp", "user/save", null);
    private final SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
    private final SymbolId service = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "()V");
    private final SymbolId table = SymbolId.logicalPath(SymbolKind.DB_TABLE, "shop", "_root", "db", "users", null);

    @Test
    void findsJspBackendFlowPathsThroughActionServiceAndTable() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(new FactKey(jsp, RelationType.SUBMITS_TO, actionPath, "/user/save"), "edit.jsp", 5, Confidence.CERTAIN, SourceType.JSP_FALLBACK));
        repository.upsertFact(active(new FactKey(actionPath, RelationType.ROUTES_TO, action, "struts-action"), "struts-config.xml", 12, Confidence.CERTAIN, SourceType.STRUTS_CONFIG));
        repository.upsertFact(active(new FactKey(action, RelationType.CALLS, service, "direct"), "UserAction.java", 20, Confidence.LIKELY, SourceType.SPOON));
        repository.upsertFact(active(new FactKey(service, RelationType.WRITES_TABLE, table, "insert users"), "UserService.java", 31, Confidence.LIKELY, SourceType.SQL));

        List<GraphFlowPath> paths = new GraphFlowQueryEngine().findDownstreamPaths(
            repository.activeFacts("shop", "snapshot-1"),
            jsp,
            GraphFlowQueryEngine.JSP_BACKEND_FLOW_RELATIONS,
            4,
            10
        );

        assertEquals(4, paths.size());
        GraphFlowPath tablePath = paths.getLast();
        assertEquals(table, tablePath.endpoint());
        assertEquals(5, tablePath.steps().size());
        assertEquals(RelationType.WRITES_TABLE, tablePath.steps().getLast().incomingRelation());
        assertTrue(tablePath.sourceTypes().contains(SourceType.JSP_FALLBACK));
    }

    @Test
    void exportsJspBackendFlowJsonWithEvidenceKeys() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(new FactKey(jsp, RelationType.SUBMITS_TO, actionPath, "/user/save"), "edit.jsp", 5, Confidence.CERTAIN, SourceType.JSP_FALLBACK));
        List<GraphFlowPath> paths = new GraphFlowQueryEngine().findDownstreamPaths(repository.activeFacts("shop", "snapshot-1"), jsp, GraphFlowQueryEngine.JSP_BACKEND_FLOW_RELATIONS, 2, 10);

        String json = new GraphFlowJsonExporter().export("shop", "snapshot-1", jsp.value(), paths);

        assertTrue(json.contains("\"startSymbolId\""));
        assertTrue(json.contains("SUBMITS_TO"));
        assertTrue(json.contains("\"evidenceKeys\""));
        assertTrue(json.contains("edit.jsp"));
    }

    @Test
    void findsCallersThroughIncomingCallEdges() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(new FactKey(action, RelationType.CALLS, service, "direct"), "UserAction.java", 20, Confidence.LIKELY, SourceType.SPOON));

        List<GraphFlowPath> paths = new GraphFlowQueryEngine().findUpstreamPaths(
            repository.activeFacts("shop", "snapshot-1"),
            service,
            GraphFlowQueryEngine.CALL_GRAPH_RELATIONS,
            2,
            10
        );

        assertEquals(1, paths.size());
        assertEquals(action, paths.getFirst().endpoint());
        assertEquals(RelationType.CALLS, paths.getFirst().steps().getLast().incomingRelation());
        assertTrue(paths.getFirst().steps().getLast().evidenceKeys().stream().anyMatch(key -> key.path().equals("UserAction.java")));
    }

    @Test
    void findsCalleesThroughOutgoingCallEdges() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(new FactKey(action, RelationType.CALLS, service, "direct"), "UserAction.java", 20, Confidence.LIKELY, SourceType.SPOON));

        List<GraphFlowPath> paths = new GraphFlowQueryEngine().findDownstreamPaths(
            repository.activeFacts("shop", "snapshot-1"),
            action,
            GraphFlowQueryEngine.CALL_GRAPH_RELATIONS,
            2,
            10
        );

        assertEquals(1, paths.size());
        assertEquals(service, paths.getFirst().endpoint());
        assertEquals(RelationType.CALLS, paths.getFirst().steps().getLast().incomingRelation());
    }

    private GraphFact active(FactKey factKey, String path, int line, Confidence confidence, SourceType sourceType) {
        return GraphFact.active(
            factKey,
            new EvidenceKey(sourceType, "test", path, line, line, factKey.qualifier()),
            "shop",
            "snapshot-1",
            "run-1",
            path,
            confidence,
            sourceType
        );
    }
}
