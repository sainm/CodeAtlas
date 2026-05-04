package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.symbols.IdentityType;

class GraphNodeTypeRegistryTest {
    @Test
    void registersCoreNodesFromTaskList() {
        GraphNodeTypeRegistry registry = GraphNodeTypeRegistry.defaults();

        for (String label : List.of(
                "Project",
                "Module",
                "SourceFile",
                "Class",
                "Method",
                "Field",
                "JspPage",
                "JspInclude",
                "JspTag",
                "JspExpression",
                "JspScriptlet",
                "HtmlPage",
                "HtmlForm",
                "HtmlInput",
                "HtmlLink",
                "ScriptResource",
                "DomEventHandler",
                "ClientRequest",
                "JspForm",
                "JspInput",
                "ApiEndpoint",
                "ActionPath",
                "DiconComponent",
                "EntryPoint",
                "ParamSlot",
                "ReturnSlot",
                "RequestParameter",
                "RequestAttribute",
                "SessionAttribute",
                "ModelAttribute",
                "Schedule",
                "CronTrigger",
                "MessageQueue",
                "MessageTopic",
                "DomainEvent",
                "MessageListener",
                "BatchJob",
                "CliCommand",
                "ShellScript",
                "ExternalCommand",
                "SqlStatement",
                "SqlParameter",
                "SqlVariant",
                "SqlBranchCondition",
                "MethodSummary",
                "HtmlRenderSlot",
                "ClientInitData",
                "ReflectionCandidate",
                "DataSource",
                "DbSchema",
                "DbTable",
                "DbColumn",
                "DbIndex",
                "DbConstraint",
                "DbView",
                "ConfigKey",
                "ReportDefinition",
                "ReportField",
                "SyntheticSymbol",
                "FeatureSeed",
                "FeatureScope",
                "ChangePlan",
                "ArchitectureHealth",
                "ArchitectureMetric",
                "HotspotCandidate",
                "ImportReview",
                "AnalysisScopeDecision",
                "ChangeItem",
                "SavedQuery",
                "WatchSubscription",
                "ReviewComment",
                "PolicyViolation",
                "ExportArtifact",
                "NativeLibrary",
                "BoundarySymbol")) {
            GraphNodeType nodeType = registry.require(label);

            assertEquals(label, nodeType.label());
            assertTrue(nodeType.defaultQueryFact() || nodeType.layer() == GraphNodeLayer.PLANNING_REVIEW);
        }
    }

    @Test
    void assignsIdentityTypeAndLayerBoundaries() {
        GraphNodeTypeRegistry registry = GraphNodeTypeRegistry.defaults();

        assertSame(GraphNodeLayer.CODE_STRUCTURE, registry.require("Project").layer());
        assertSame(GraphNodeLayer.FRAMEWORK_SEMANTICS, registry.require("ApiEndpoint").layer());
        assertSame(GraphNodeLayer.IMPACT_FLOW, registry.require("SqlParameter").layer());
        assertSame(GraphNodeLayer.IMPACT_FLOW, registry.require("ParamSlot").layer());
        assertSame(GraphNodeLayer.IMPACT_FLOW, registry.require("ReflectionCandidate").layer());
        assertSame(GraphNodeLayer.FRAMEWORK_SEMANTICS, registry.require("DataSource").layer());
        assertSame(GraphNodeLayer.FRAMEWORK_SEMANTICS, registry.require("DbView").layer());
        assertSame(GraphNodeLayer.PLANNING_REVIEW, registry.require("FeatureSeed").layer());
        assertSame(IdentityType.SYMBOL_ID, registry.require("Method").identityType());
        assertSame(IdentityType.SYMBOL_ID, registry.require("DbIndex").identityType());
        assertSame(IdentityType.SYMBOL_ID, registry.require("SyntheticSymbol").identityType());
        assertSame(IdentityType.FLOW_ID, registry.require("MethodSummary").identityType());
        assertSame(IdentityType.FLOW_ID, registry.require("JspInput").identityType());
        assertSame(IdentityType.ARTIFACT_ID, registry.require("ImportReview").identityType());
        assertSame(IdentityType.ARTIFACT_ID, registry.require("SavedQuery").identityType());
        assertEquals("symbolId", registry.require("DataSource").identityProperty());
        assertEquals("symbolId", registry.require("DbTable").identityProperty());
        assertEquals("symbolId", registry.require("SyntheticSymbol").identityProperty());
        assertEquals("flowId", registry.require("RequestParameter").identityProperty());
        assertEquals("flowId", registry.require("SqlParameter").identityProperty());
        assertEquals("artifactId", registry.require("ChangePlan").identityProperty());
        assertEquals("artifactId", registry.require("ChangeItem").identityProperty());
    }

    @Test
    void rejectsUnknownNodeLabels() {
        GraphNodeTypeRegistry registry = GraphNodeTypeRegistry.defaults();

        assertThrows(IllegalArgumentException.class, () -> registry.require("Bean"));
    }
}
