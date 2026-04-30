package org.sainm.codeatlas.analyzers.java;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestParameterGraphBuilderTest {
    @Test
    void turnsRequestEventsIntoParameterFacts() {
        var method = org.sainm.codeatlas.graph.model.SymbolId.method(
            "shop",
            "_root",
            "src/main/java",
            "com.acme.UserAction",
            "execute",
            "()V"
        );
        VariableTraceResult traceResult = new VariableTraceResult(List.of(
            new VariableEvent(method, "id", VariableEventKind.REQUEST_PARAMETER_READ, "request.getParameter(\"id\")", 12),
            new VariableEvent(method, "result", VariableEventKind.REQUEST_ATTRIBUTE_WRITE, "request.setAttribute(\"result\", value)", 13)
        ), List.of(
            new MethodArgumentFlowEvent(
                method,
                org.sainm.codeatlas.graph.model.SymbolId.method(
                    "shop",
                    "_root",
                    "src/main/java",
                    "com.acme.UserService",
                    "save",
                    "(java.lang.String):void"
                ),
                "id",
                "id",
                "request-parameter-local",
                "service.save(id)",
                14
            )
        ));
        AnalyzerScope scope = new AnalyzerScope("shop", "_root", "snapshot-1", "run-1", "scope-java", Path.of("."));

        RequestParameterGraphResult result = new RequestParameterGraphBuilder().build(scope, "shop", traceResult);

        assertTrue(result.nodes().stream().anyMatch(node -> node.symbolId().kind() == SymbolKind.REQUEST_PARAMETER
            && node.symbolId().ownerQualifiedName().equals("id")));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.READS_PARAM));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.WRITES_PARAM));
        assertTrue(result.facts().stream().anyMatch(fact -> fact.factKey().relationType() == RelationType.PASSES_PARAM
            && fact.factKey().target().ownerQualifiedName().equals("com.acme.UserService")
            && fact.factKey().qualifier().contains("request-parameter-local:id")));
    }
}
