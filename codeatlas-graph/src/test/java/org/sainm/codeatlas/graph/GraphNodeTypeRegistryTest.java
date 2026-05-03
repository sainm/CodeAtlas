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
                "EntryPoint",
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
                "DbSchema",
                "DbTable",
                "DbColumn",
                "ConfigKey",
                "ReportDefinition",
                "ReportField",
                "FeatureSeed",
                "FeatureScope",
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
        assertSame(GraphNodeLayer.PLANNING_REVIEW, registry.require("FeatureSeed").layer());
        assertSame(IdentityType.SYMBOL_ID, registry.require("Method").identityType());
        assertSame(IdentityType.FLOW_ID, registry.require("JspInput").identityType());
        assertSame(IdentityType.ARTIFACT_ID, registry.require("SavedQuery").identityType());
        assertEquals("symbolId", registry.require("DbTable").identityProperty());
        assertEquals("flowId", registry.require("SqlParameter").identityProperty());
        assertEquals("artifactId", registry.require("ChangeItem").identityProperty());
    }

    @Test
    void rejectsUnknownNodeLabels() {
        GraphNodeTypeRegistry registry = GraphNodeTypeRegistry.defaults();

        assertThrows(IllegalArgumentException.class, () -> registry.require("Bean"));
    }
}
