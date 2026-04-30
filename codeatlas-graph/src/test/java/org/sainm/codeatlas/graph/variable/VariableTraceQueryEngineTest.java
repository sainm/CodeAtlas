package org.sainm.codeatlas.graph.variable;

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

class VariableTraceQueryEngineTest {
    private final SymbolId parameter = SymbolId.logicalPath(SymbolKind.REQUEST_PARAMETER, "shop", "_root", "_request", "userId", null);
    private final SymbolId page = SymbolId.logicalPath(SymbolKind.JSP_PAGE, "shop", "_root", "src/main/webapp", "user/edit.jsp", null);
    private final SymbolId form = SymbolId.logicalPath(SymbolKind.JSP_FORM, "shop", "_root", "src/main/webapp", "user/edit.jsp", "form:0");
    private final SymbolId input = SymbolId.logicalPath(SymbolKind.JSP_INPUT, "shop", "_root", "src/main/webapp", "user/edit.jsp", "input:userId");
    private final SymbolId action = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserAction", "execute", "()V");
    private final SymbolId service = SymbolId.method("shop", "_root", "src/main/java", "com.acme.UserService", "save", "(Ljava/lang/String;)V");
    private final SymbolId formClass = SymbolId.classSymbol("shop", "_root", "src/main/java", "com.acme.UserForm");
    private final SymbolId validator = SymbolId.logicalPath(SymbolKind.CONFIG_KEY, "shop", "_root", "src/main/webapp", "WEB-INF/validation.xml", "form:userForm.userId");
    private final SymbolId table = SymbolId.logicalPath(SymbolKind.DB_TABLE, "shop", "_root", "db", "users", null);

    @Test
    void findsVariableSourcePathsFromWriters() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(new FactKey(page, RelationType.DECLARES, form, "jsp-form"), "edit.jsp", 8, Confidence.CERTAIN));
        repository.upsertFact(active(new FactKey(form, RelationType.DECLARES, input, "jsp-input:userId"), "edit.jsp", 12, Confidence.CERTAIN));
        repository.upsertFact(active(new FactKey(input, RelationType.WRITES_PARAM, parameter, "userId"), "edit.jsp", 12, Confidence.CERTAIN));

        List<VariableTracePath> paths = new VariableTraceQueryEngine().findSourcePaths(
            repository.activeFacts("shop", "snapshot-1"),
            parameter,
            4,
            10
        );

        assertEquals(3, paths.size());
        assertEquals(VariableTraceDirection.SOURCE, paths.getFirst().direction());
        assertEquals(input, paths.getFirst().endpoint());
        assertEquals(RelationType.WRITES_PARAM, paths.getFirst().steps().get(1).incomingRelation());
        assertTrue(!paths.getFirst().steps().get(1).evidenceKeys().isEmpty());
        assertTrue(paths.stream().anyMatch(path -> path.endpoint().equals(form)
            && path.steps().stream().anyMatch(step -> step.incomingRelation() == RelationType.DECLARES)));
        assertTrue(paths.stream().anyMatch(path -> path.endpoint().equals(page)
            && path.steps().stream().filter(step -> step.incomingRelation() == RelationType.DECLARES).count() == 2));
    }

    @Test
    void findsVariableSinkPathsFromReadersBindingsAndValidatorCoverage() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(new FactKey(action, RelationType.READS_PARAM, parameter, "request.getParameter:userId"), "UserAction.java", 20, Confidence.LIKELY));
        repository.upsertFact(active(new FactKey(parameter, RelationType.BINDS_TO, formClass, "form-property:userId"), "struts-config.xml", 31, Confidence.CERTAIN));
        repository.upsertFact(active(new FactKey(parameter, RelationType.COVERED_BY, validator, "validator:userId"), "validation.xml", 8, Confidence.CERTAIN));

        List<VariableTracePath> paths = new VariableTraceQueryEngine().findSinkPaths(
            repository.activeFacts("shop", "snapshot-1"),
            parameter,
            2,
            10
        );

        assertEquals(3, paths.size());
        assertTrue(paths.stream().anyMatch(path -> path.endpoint().equals(action)));
        assertTrue(paths.stream().anyMatch(path -> path.endpoint().equals(formClass)));
        assertTrue(paths.stream().anyMatch(path -> path.endpoint().equals(validator)));
    }

    @Test
    void followsDownstreamCallsAndTableEffectsAfterParameterRead() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(new FactKey(action, RelationType.READS_PARAM, parameter, "request.getParameter:userId"), "UserAction.java", 20, Confidence.LIKELY));
        repository.upsertFact(active(new FactKey(action, RelationType.PASSES_PARAM, service, "request-parameter:userId argument:userId"), "UserAction.java", 21, Confidence.LIKELY));
        repository.upsertFact(active(new FactKey(service, RelationType.WRITES_TABLE, table, "insert users"), "UserService.java", 31, Confidence.LIKELY));

        List<VariableTracePath> paths = new VariableTraceQueryEngine().findSinkPaths(
            repository.activeFacts("shop", "snapshot-1"),
            parameter,
            3,
            10
        );

        assertTrue(paths.stream().anyMatch(path -> path.endpoint().equals(service)
            && path.steps().stream().anyMatch(step -> step.incomingRelation() == RelationType.PASSES_PARAM)));
        assertTrue(paths.stream().anyMatch(path -> path.endpoint().equals(table)
            && path.steps().stream().anyMatch(step -> step.incomingRelation() == RelationType.WRITES_TABLE)));
    }

    @Test
    void exportsTracePathsAsEvidenceCarryingJson() {
        InMemoryGraphRepository repository = new InMemoryGraphRepository();
        repository.upsertFact(active(new FactKey(input, RelationType.WRITES_PARAM, parameter, "userId"), "edit.jsp", 12, Confidence.CERTAIN));
        List<VariableTracePath> paths = new VariableTraceQueryEngine().findSourcePaths(repository.activeFacts("shop", "snapshot-1"), parameter, 2, 10);

        String json = new VariableTraceJsonExporter().export("shop", "snapshot-1", parameter.value(), paths);

        assertTrue(json.contains("\"direction\":\"SOURCE\""));
        assertTrue(json.contains("\"directionLabel\":\"值从哪里来\""));
        assertTrue(json.contains("\"parameterDisplayName\":\"输入参数 userId\""));
        assertTrue(json.contains("\"displayName\":\"页面输入 userId\""));
        assertTrue(json.contains("\"symbolKindLabel\":\"页面输入\""));
        assertTrue(json.contains("\"incomingRelation\":\"WRITES_PARAM\""));
        assertTrue(json.contains("\"evidenceKeys\""));
        assertTrue(json.contains("edit.jsp"));
    }

    private GraphFact active(FactKey factKey, String path, int line, Confidence confidence) {
        return GraphFact.active(
            factKey,
            new EvidenceKey(SourceType.JSP_FALLBACK, "test", path, line, line, factKey.qualifier()),
            "shop",
            "snapshot-1",
            "run-1",
            path,
            confidence,
            SourceType.JSP_FALLBACK
        );
    }
}
