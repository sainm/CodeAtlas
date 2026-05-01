package org.sainm.codeatlas.analyzers.struts;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.analyzers.java.SpoonSymbolMapper;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

public final class StrutsActionForwardAnalyzer {
    public StrutsActionForwardAnalysisResult analyze(
        AnalyzerScope scope,
        String projectKey,
        String javaSourceRootKey,
        String strutsSourceRootKey,
        List<Path> sourceFiles
    ) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(25);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setCommentEnabled(false);
        sourceFiles.forEach(file -> launcher.addInputResource(file.toString()));
        CtModel model = launcher.buildModel();

        SpoonSymbolMapper symbols = new SpoonSymbolMapper(projectKey, scope.moduleKey(), javaSourceRootKey);
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();
        for (CtType<?> type : model.getAllTypes()) {
            for (CtMethod<?> method : type.getMethods()) {
                SymbolId methodSymbol = symbols.method(method);
                for (CtInvocation<?> invocation : method.getElements(new TypeFilter<>(CtInvocation.class))) {
                    addFindForwardFact(scope, projectKey, strutsSourceRootKey, methodSymbol, invocation, nodes, facts);
                    addSendRedirectFact(scope, projectKey, strutsSourceRootKey, methodSymbol, invocation, nodes, facts);
                }
                for (CtConstructorCall<?> constructorCall : method.getElements(new TypeFilter<>(CtConstructorCall.class))) {
                    addDirectActionForwardFact(scope, projectKey, strutsSourceRootKey, methodSymbol, constructorCall, nodes, facts);
                }
            }
        }
        return new StrutsActionForwardAnalysisResult(nodes, facts);
    }

    private void addDirectActionForwardFact(
        AnalyzerScope scope,
        String projectKey,
        String strutsSourceRootKey,
        SymbolId methodSymbol,
        CtConstructorCall<?> constructorCall,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        if (constructorCall.getType() == null || !isActionForwardType(constructorCall.getType().getQualifiedName())) {
            return;
        }
        if (constructorCall.getArguments().isEmpty()) {
            return;
        }
        String targetValue = directActionForwardTarget(constructorCall);
        if (targetValue.isBlank()) {
            return;
        }
        SymbolId target = jspOrActionTarget(projectKey, scope.moduleKey(), strutsSourceRootKey, targetValue);
        nodes.add(GraphNodeFactory.methodNode(methodSymbol, NodeRole.CODE_MEMBER));
        addTargetNode(nodes, target);
        String constructType = constructorCall.getType().getQualifiedName().endsWith("ActionRedirect") ? "ActionRedirect" : "ActionForward";
        facts.add(GraphFact.active(
            new FactKey(methodSymbol, RelationType.FORWARDS_TO, target, "new " + constructType + ":" + targetValue),
            evidence(constructorCall.getPosition(), targetValue),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            Confidence.LIKELY,
            SourceType.SPOON
        ));
    }

    private String directActionForwardTarget(CtConstructorCall<?> constructorCall) {
        int pathArgumentIndex = constructorCall.getArguments().size() >= 3 ? 1 : 0;
        return stripQuotes(constructorCall.getArguments().get(pathArgumentIndex).toString());
    }

    private void addSendRedirectFact(
        AnalyzerScope scope,
        String projectKey,
        String strutsSourceRootKey,
        SymbolId methodSymbol,
        CtInvocation<?> invocation,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        if (invocation.getExecutable() == null || !"sendRedirect".equals(invocation.getExecutable().getSimpleName())) {
            return;
        }
        if (invocation.getArguments().isEmpty()) {
            return;
        }
        String targetValue = stripQuotes(invocation.getArguments().getFirst().toString());
        if (targetValue.isBlank()) {
            return;
        }
        SymbolId target = jspOrActionTarget(projectKey, scope.moduleKey(), strutsSourceRootKey, targetValue);
        nodes.add(GraphNodeFactory.methodNode(methodSymbol, NodeRole.CODE_MEMBER));
        addTargetNode(nodes, target);
        facts.add(GraphFact.active(
            new FactKey(methodSymbol, RelationType.FORWARDS_TO, target, "sendRedirect:" + targetValue),
            evidence(invocation.getPosition(), targetValue),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            Confidence.LIKELY,
            SourceType.SPOON
        ));
    }

    private boolean isActionForwardType(String qualifiedName) {
        return "org.apache.struts.action.ActionForward".equals(qualifiedName)
            || "org.apache.struts.action.ActionRedirect".equals(qualifiedName);
    }

    private void addFindForwardFact(
        AnalyzerScope scope,
        String projectKey,
        String strutsSourceRootKey,
        SymbolId methodSymbol,
        CtInvocation<?> invocation,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        if (invocation.getExecutable() == null || !"findForward".equals(invocation.getExecutable().getSimpleName())) {
            return;
        }
        if (invocation.getArguments().isEmpty()) {
            return;
        }
        String forwardName = stripQuotes(invocation.getArguments().getFirst().toString());
        if (forwardName.isBlank()) {
            return;
        }
        SymbolId forwardConfig = StrutsConfigAnalyzer.forwardConfigSymbol(
            projectKey,
            scope.moduleKey(),
            strutsSourceRootKey,
            forwardName
        );
        nodes.add(GraphNodeFactory.methodNode(methodSymbol, NodeRole.CODE_MEMBER));
        nodes.add(GraphNodeFactory.configNode(forwardConfig));
        facts.add(GraphFact.active(
            new FactKey(methodSymbol, RelationType.FORWARDS_TO, forwardConfig, "mapping.findForward:" + forwardName),
            evidence(invocation.getPosition(), forwardName),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            Confidence.LIKELY,
            SourceType.SPOON
        ));
    }

    private EvidenceKey evidence(SourcePosition position, String forwardName) {
        if (position == null || !position.isValidPosition() || position.getFile() == null) {
            return new EvidenceKey(SourceType.SPOON, "struts-action-forward", "_unknown", 0, 0, forwardName);
        }
        return new EvidenceKey(
            SourceType.SPOON,
            "struts-action-forward",
            position.getFile().toPath().toString(),
            Math.max(0, position.getLine()),
            Math.max(0, position.getEndLine()),
            forwardName
        );
    }

    private SymbolId jspOrActionTarget(String projectKey, String moduleKey, String sourceRootKey, String targetValue) {
        if (targetValue.endsWith(".do") || !targetValue.contains(".")) {
            return StrutsConfigAnalyzer.actionPath(projectKey, moduleKey, sourceRootKey, targetValue);
        }
        return SymbolId.logicalPath(SymbolKind.JSP_PAGE, projectKey, moduleKey, sourceRootKey, targetValue, null);
    }

    private void addTargetNode(List<GraphNode> nodes, SymbolId target) {
        if (target.kind() == SymbolKind.ACTION_PATH) {
            nodes.add(GraphNodeFactory.actionPathNode(target));
        } else if (target.kind() == SymbolKind.JSP_PAGE) {
            nodes.add(GraphNodeFactory.jspNode(target, NodeRole.JSP_ARTIFACT));
        } else {
            nodes.add(GraphNodeFactory.configNode(target));
        }
    }

    private String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2
            && ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return "";
    }
}
