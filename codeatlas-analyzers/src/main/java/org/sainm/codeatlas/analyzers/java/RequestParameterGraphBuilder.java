package org.sainm.codeatlas.analyzers.java;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import org.sainm.codeatlas.graph.model.GraphNodeFactory;
import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.util.ArrayList;
import java.util.List;

public final class RequestParameterGraphBuilder {
    public RequestParameterGraphResult build(AnalyzerScope scope, String projectKey, VariableTraceResult traceResult) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();
        for (VariableEvent event : traceResult.events()) {
            RelationType relationType = relationType(event.kind());
            if (relationType == null || event.variableName().isBlank()) {
                continue;
            }
            SymbolId parameter = SymbolId.logicalPath(
                SymbolKind.REQUEST_PARAMETER,
                projectKey,
                event.methodSymbol().moduleKey(),
                "_request",
                event.variableName(),
                null
            );
            nodes.add(GraphNodeFactory.requestParameterNode(parameter));
            facts.add(GraphFact.active(
                new FactKey(event.methodSymbol(), relationType, parameter, event.variableName()),
                new EvidenceKey(SourceType.SPOON, "request-parameter", "_unknown", event.line(), event.line(), event.variableName()),
                scope.projectId(),
                scope.snapshotId(),
                scope.analysisRunId(),
                scope.scopeKey(),
                Confidence.LIKELY,
                SourceType.SPOON
            ));
        }
        for (MethodArgumentFlowEvent flow : traceResult.argumentFlows()) {
            nodes.add(GraphNodeFactory.methodNode(flow.calleeMethodSymbol(), NodeRole.CODE_MEMBER));
            if (!flow.requestParameterName().isBlank()) {
                SymbolId parameter = requestParameter(projectKey, flow.callerMethodSymbol(), flow.requestParameterName());
                nodes.add(GraphNodeFactory.requestParameterNode(parameter));
            }
            facts.add(GraphFact.active(
                new FactKey(flow.callerMethodSymbol(), RelationType.PASSES_PARAM, flow.calleeMethodSymbol(), flowQualifier(flow)),
                new EvidenceKey(SourceType.SPOON, "method-argument-flow", "_unknown", flow.line(), flow.line(), flow.expression()),
                scope.projectId(),
                scope.snapshotId(),
                scope.analysisRunId(),
                scope.scopeKey(),
                flow.requestParameterName().isBlank() ? Confidence.POSSIBLE : Confidence.LIKELY,
                SourceType.SPOON
            ));
        }
        return new RequestParameterGraphResult(nodes, facts);
    }

    private SymbolId requestParameter(String projectKey, SymbolId methodSymbol, String parameterName) {
        return SymbolId.logicalPath(
            SymbolKind.REQUEST_PARAMETER,
            projectKey,
            methodSymbol.moduleKey(),
            "_request",
            parameterName,
            null
        );
    }

    private String flowQualifier(MethodArgumentFlowEvent flow) {
        if (flow.requestParameterName().isBlank()) {
            return "method-parameter:" + flow.argumentName();
        }
        return flow.flowKind() + ":" + flow.requestParameterName() + " argument:" + flow.argumentName();
    }

    private RelationType relationType(VariableEventKind kind) {
        return switch (kind) {
            case REQUEST_PARAMETER_READ, REQUEST_ATTRIBUTE_READ -> RelationType.READS_PARAM;
            case REQUEST_ATTRIBUTE_WRITE -> RelationType.WRITES_PARAM;
            default -> null;
        };
    }
}
