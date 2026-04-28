package org.sainm.codeatlas.analyzers.jsp;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.analyzers.struts.StrutsConfigAnalyzer;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JspFormAnalyzer {
    private final TolerantJspFormExtractor extractor = new TolerantJspFormExtractor();

    public JspAnalysisResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, Path jspFile) {
        try {
            String jspText = Files.readString(jspFile);
            List<JspForm> forms = extractor.extract(jspText);
            List<GraphNode> nodes = new ArrayList<>();
            List<GraphFact> facts = new ArrayList<>();

            SymbolId jspPage = SymbolId.logicalPath(SymbolKind.JSP_PAGE, projectKey, scope.moduleKey(), sourceRootKey, jspFile.toString(), null);
            nodes.add(GraphNodeFactory.jspNode(jspPage, NodeRole.JSP_ARTIFACT));
            for (int i = 0; i < forms.size(); i++) {
                addForm(scope, projectKey, sourceRootKey, jspFile, jspPage, forms.get(i), i, nodes, facts);
            }
            return new JspAnalysisResult(forms, nodes, facts);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to analyze JSP forms: " + jspFile, exception);
        }
    }

    private void addForm(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path jspFile,
        SymbolId jspPage,
        JspForm form,
        int index,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId formSymbol = SymbolId.logicalPath(SymbolKind.JSP_FORM, projectKey, scope.moduleKey(), sourceRootKey, jspFile.toString(), "form:" + index);
        SymbolId actionPath = StrutsConfigAnalyzer.actionPath(projectKey, scope.moduleKey(), sourceRootKey, form.action());
        nodes.add(GraphNodeFactory.jspNode(formSymbol, NodeRole.JSP_ARTIFACT));
        nodes.add(GraphNodeFactory.actionPathNode(actionPath));
        facts.add(fact(scope, jspPage, RelationType.DECLARES, formSymbol, jspFile, form.line(), "jsp-form", Confidence.CERTAIN));
        facts.add(fact(scope, formSymbol, RelationType.SUBMITS_TO, actionPath, jspFile, form.line(), "form-action", Confidence.LIKELY));

        for (JspInput input : form.inputs()) {
            SymbolId inputSymbol = SymbolId.logicalPath(
                SymbolKind.JSP_INPUT,
                projectKey,
                scope.moduleKey(),
                sourceRootKey,
                jspFile.toString(),
                "form:" + index + ":input:" + input.name()
            );
            SymbolId parameter = SymbolId.logicalPath(
                SymbolKind.REQUEST_PARAMETER,
                projectKey,
                scope.moduleKey(),
                "_request",
                input.name(),
                null
            );
            nodes.add(GraphNodeFactory.jspNode(inputSymbol, NodeRole.JSP_ARTIFACT));
            nodes.add(GraphNodeFactory.requestParameterNode(parameter));
            facts.add(fact(scope, formSymbol, RelationType.DECLARES, inputSymbol, jspFile, input.line(), "jsp-input:" + input.name(), Confidence.CERTAIN));
            facts.add(fact(scope, inputSymbol, RelationType.WRITES_PARAM, actionPath, jspFile, input.line(), input.name(), Confidence.LIKELY));
            facts.add(fact(scope, inputSymbol, RelationType.WRITES_PARAM, parameter, jspFile, input.line(), input.name(), Confidence.LIKELY));
        }
    }

    private GraphFact fact(
        AnalyzerScope scope,
        SymbolId source,
        RelationType relationType,
        SymbolId target,
        Path jspFile,
        int line,
        String qualifier,
        Confidence confidence
    ) {
        return GraphFact.active(
            new FactKey(source, relationType, target, qualifier),
            new EvidenceKey(SourceType.JSP_FALLBACK, "tolerant-jsp-form", jspFile.toString(), line, line, qualifier),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            confidence,
            SourceType.JSP_FALLBACK
        );
    }
}
