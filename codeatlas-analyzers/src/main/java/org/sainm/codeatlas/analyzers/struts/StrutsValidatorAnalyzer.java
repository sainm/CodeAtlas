package org.sainm.codeatlas.analyzers.struts;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.analyzers.xml.SafeXmlDocumentLoader;
import org.sainm.codeatlas.graph.model.Confidence;
import org.sainm.codeatlas.graph.model.EvidenceKey;
import org.sainm.codeatlas.graph.model.FactKey;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import org.sainm.codeatlas.graph.model.GraphNodeFactory;
import org.sainm.codeatlas.graph.model.RelationType;
import org.sainm.codeatlas.graph.model.SourceType;
import org.sainm.codeatlas.graph.model.SymbolId;
import org.sainm.codeatlas.graph.model.SymbolKind;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class StrutsValidatorAnalyzer {
    private final SafeXmlDocumentLoader xmlLoader = new SafeXmlDocumentLoader();

    public StrutsValidatorAnalysisResult analyze(AnalyzerScope scope, String projectKey, String sourceRootKey, Path validatorXml) {
        Document document = xmlLoader.load(validatorXml);
        List<StrutsValidatorForm> forms = new ArrayList<>();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();

        NodeList formNodes = document.getElementsByTagName("form");
        for (int i = 0; i < formNodes.getLength(); i++) {
            Element form = (Element) formNodes.item(i);
            String formName = form.getAttribute("name");
            if (formName == null || formName.isBlank()) {
                continue;
            }
            StrutsValidatorForm validatorForm = new StrutsValidatorForm(formName, fields(form));
            forms.add(validatorForm);
            addFormFacts(scope, projectKey, sourceRootKey, validatorXml, validatorForm, nodes, facts);
        }

        return new StrutsValidatorAnalysisResult(forms, nodes, facts);
    }

    private List<StrutsValidatorField> fields(Element form) {
        List<StrutsValidatorField> fields = new ArrayList<>();
        NodeList fieldNodes = form.getElementsByTagName("field");
        for (int i = 0; i < fieldNodes.getLength(); i++) {
            Element field = (Element) fieldNodes.item(i);
            String property = field.getAttribute("property");
            if (property == null || property.isBlank()) {
                continue;
            }
            fields.add(new StrutsValidatorField(property, depends(field.getAttribute("depends"))));
        }
        return fields;
    }

    private List<String> depends(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(part -> !part.isBlank())
            .toList();
    }

    private void addFormFacts(
        AnalyzerScope scope,
        String projectKey,
        String sourceRootKey,
        Path validatorXml,
        StrutsValidatorForm form,
        List<GraphNode> nodes,
        List<GraphFact> facts
    ) {
        SymbolId formConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, validatorXml, "validator-form:" + form.name());
        nodes.add(GraphNodeFactory.configNode(formConfig));

        for (StrutsValidatorField field : form.fields()) {
            SymbolId fieldConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, validatorXml, "validator-form:" + form.name() + ":field:" + field.property());
            SymbolId parameter = SymbolId.logicalPath(SymbolKind.REQUEST_PARAMETER, projectKey, scope.moduleKey(), "_request", field.property(), null);
            nodes.add(GraphNodeFactory.configNode(fieldConfig));
            nodes.add(GraphNodeFactory.requestParameterNode(parameter));
            facts.add(fact(scope, formConfig, RelationType.DECLARES, fieldConfig, validatorXml, "validator-field:" + field.property(), Confidence.CERTAIN));
            facts.add(fact(scope, parameter, RelationType.COVERED_BY, fieldConfig, validatorXml, field.property(), Confidence.CERTAIN));
            for (String dependency : field.depends()) {
                SymbolId dependencyConfig = configSymbol(projectKey, scope.moduleKey(), sourceRootKey, validatorXml, "validator-rule:" + dependency);
                nodes.add(GraphNodeFactory.configNode(dependencyConfig));
                facts.add(fact(scope, fieldConfig, RelationType.USES_CONFIG, dependencyConfig, validatorXml, "validator-depends:" + dependency, Confidence.CERTAIN));
            }
        }
    }

    private SymbolId configSymbol(String projectKey, String moduleKey, String sourceRootKey, Path validatorXml, String localId) {
        return SymbolId.logicalPath(SymbolKind.CONFIG_KEY, projectKey, moduleKey, sourceRootKey, validatorXml.toString(), localId);
    }

    private GraphFact fact(
        AnalyzerScope scope,
        SymbolId source,
        RelationType relationType,
        SymbolId target,
        Path validatorXml,
        String qualifier,
        Confidence confidence
    ) {
        return GraphFact.active(
            new FactKey(source, relationType, target, qualifier),
            new EvidenceKey(SourceType.STRUTS_CONFIG, "struts-validator", validatorXml.toString(), 0, 0, qualifier),
            scope.projectId(),
            scope.snapshotId(),
            scope.analysisRunId(),
            scope.scopeKey(),
            confidence,
            SourceType.STRUTS_CONFIG
        );
    }
}
