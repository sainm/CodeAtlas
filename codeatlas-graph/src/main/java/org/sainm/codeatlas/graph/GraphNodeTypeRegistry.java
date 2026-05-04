package org.sainm.codeatlas.graph;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.sainm.codeatlas.symbols.IdentityType;

public final class GraphNodeTypeRegistry {
    private final Map<String, GraphNodeType> nodeTypesByLabel;

    private GraphNodeTypeRegistry(Map<String, GraphNodeType> nodeTypesByLabel) {
        this.nodeTypesByLabel = Collections.unmodifiableMap(new LinkedHashMap<>(nodeTypesByLabel));
    }

    public static GraphNodeTypeRegistry defaults() {
        Builder builder = new Builder();

        builder.symbol("Project", "project", GraphNodeLayer.CODE_STRUCTURE);
        builder.symbol("Module", "module", GraphNodeLayer.CODE_STRUCTURE);
        builder.symbol("SourceFile", "source-file", GraphNodeLayer.CODE_STRUCTURE);
        builder.symbol("Class", "class", GraphNodeLayer.CODE_STRUCTURE);
        builder.symbol("Method", "method", GraphNodeLayer.CODE_STRUCTURE);
        builder.symbol("Field", "field", GraphNodeLayer.CODE_STRUCTURE);

        builder.symbol("JspPage", "jsp-page", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("JspInclude", "jsp-include", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("JspTag", "jsp-tag", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("JspExpression", "jsp-expression", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("JspScriptlet", "jsp-scriptlet", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("HtmlPage", "html-page", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("HtmlForm", "html-form", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.flow("HtmlInput", "html-input", GraphNodeLayer.IMPACT_FLOW);
        builder.symbol("HtmlLink", "html-link", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("ScriptResource", "script-resource", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.flow("DomEventHandler", "dom-event-handler", GraphNodeLayer.IMPACT_FLOW);
        builder.symbol("ClientRequest", "client-request", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("JspForm", "jsp-form", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.flow("JspInput", "jsp-input", GraphNodeLayer.IMPACT_FLOW);
        builder.symbol("ApiEndpoint", "api-endpoint", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("ActionPath", "action-path", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("DiconComponent", "dicon-component", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("EntryPoint", "entrypoint", GraphNodeLayer.IMPACT_FLOW);
        builder.flow("ParamSlot", "param-slot", GraphNodeLayer.IMPACT_FLOW);
        builder.flow("ReturnSlot", "return-slot", GraphNodeLayer.IMPACT_FLOW);
        builder.flow("RequestParameter", "request-param", GraphNodeLayer.IMPACT_FLOW);
        builder.flow("RequestAttribute", "request-attr", GraphNodeLayer.IMPACT_FLOW);
        builder.flow("SessionAttribute", "session-attr", GraphNodeLayer.IMPACT_FLOW);
        builder.flow("ModelAttribute", "model-attr", GraphNodeLayer.IMPACT_FLOW);
        builder.symbol("Schedule", "schedule", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("CronTrigger", "cron-trigger", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("MessageQueue", "message-queue", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("MessageTopic", "message-topic", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("DomainEvent", "domain-event", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("MessageListener", "message-listener", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("BatchJob", "batch-job", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("CliCommand", "cli-command", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("ShellScript", "shell-script", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("ExternalCommand", "external-command", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("SqlStatement", "sql-statement", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.flow("SqlParameter", "sql-param", GraphNodeLayer.IMPACT_FLOW);
        builder.symbol("SqlVariant", "sql-variant", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.flow("SqlBranchCondition", "sql-branch-condition", GraphNodeLayer.IMPACT_FLOW);
        builder.flow("MethodSummary", "method-summary", GraphNodeLayer.IMPACT_FLOW);
        builder.flow("HtmlRenderSlot", "html-render-slot", GraphNodeLayer.IMPACT_FLOW);
        builder.flow("ClientInitData", "client-init-data", GraphNodeLayer.IMPACT_FLOW);
        builder.flow("ReflectionCandidate", "reflection-candidate", GraphNodeLayer.IMPACT_FLOW);
        builder.symbol("DataSource", "datasource", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("DbSchema", "db-schema", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("DbTable", "db-table", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("DbColumn", "db-column", GraphNodeLayer.IMPACT_FLOW);
        builder.symbol("DbIndex", "db-index", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("DbConstraint", "db-constraint", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("DbView", "db-view", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("ConfigKey", "config-key", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("ReportDefinition", "report-definition", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("ReportField", "report-field", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("NativeLibrary", "native-library", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("BoundarySymbol", "boundary-symbol", GraphNodeLayer.FRAMEWORK_SEMANTICS);
        builder.symbol("SyntheticSymbol", "synthetic-symbol", GraphNodeLayer.FRAMEWORK_SEMANTICS);

        builder.artifact("FeatureSeed", "feature-seed");
        builder.artifact("FeatureScope", "feature-scope");
        builder.artifact("ChangePlan", "change-plan");
        builder.artifact("ArchitectureHealth", "architecture-health");
        builder.artifact("ArchitectureMetric", "architecture-metric");
        builder.artifact("HotspotCandidate", "hotspot-candidate");
        builder.artifact("ImportReview", "import-review");
        builder.artifact("AnalysisScopeDecision", "analysis-scope-decision");
        builder.artifact("ChangeItem", "change-item");
        builder.artifact("SavedQuery", "saved-query");
        builder.artifact("WatchSubscription", "watch-subscription");
        builder.artifact("ReviewComment", "review-comment");
        builder.artifact("PolicyViolation", "policy-violation");
        builder.artifact("ExportArtifact", "export-artifact");

        return builder.build();
    }

    public GraphNodeType require(String label) {
        GraphNodeType nodeType = nodeTypesByLabel.get(label);
        if (nodeType == null) {
            throw new IllegalArgumentException("Unregistered graph node label: " + label);
        }
        return nodeType;
    }

    public boolean contains(String label) {
        return nodeTypesByLabel.containsKey(label);
    }

    public Collection<GraphNodeType> all() {
        return nodeTypesByLabel.values();
    }

    private static final class Builder {
        private final Map<String, GraphNodeType> nodeTypesByLabel = new LinkedHashMap<>();

        void symbol(String label, String kind, GraphNodeLayer layer) {
            add(label, kind, IdentityType.SYMBOL_ID, layer, true);
        }

        void flow(String label, String kind, GraphNodeLayer layer) {
            add(label, kind, IdentityType.FLOW_ID, layer, true);
        }

        void artifact(String label, String kind) {
            add(label, kind, IdentityType.ARTIFACT_ID, GraphNodeLayer.PLANNING_REVIEW, false);
        }

        private void add(String label, String kind, IdentityType identityType, GraphNodeLayer layer, boolean defaultQueryFact) {
            GraphNodeType nodeType = new GraphNodeType(
                    label,
                    kind,
                    identityType,
                    GraphNodeType.identityPropertyFor(identityType),
                    layer,
                    defaultQueryFact);
            if (nodeTypesByLabel.putIfAbsent(label, nodeType) != null) {
                throw new IllegalArgumentException("Duplicate graph node label: " + label);
            }
        }

        GraphNodeTypeRegistry build() {
            return new GraphNodeTypeRegistry(nodeTypesByLabel);
        }
    }
}
