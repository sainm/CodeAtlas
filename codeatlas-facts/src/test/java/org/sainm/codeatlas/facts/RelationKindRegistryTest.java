package org.sainm.codeatlas.facts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RelationKindRegistryTest {
    @Test
    void registersMvpRelationsFromDesign() {
        RelationKindRegistry registry = RelationKindRegistry.defaults();

        for (String relationName : List.of(
                "DECLARES",
                "CALLS",
                "ROUTES_TO",
                "DECLARES_ENTRYPOINT",
                "SUBMITS_TO",
                "BINDS_TO",
                "READS_TABLE",
                "WRITES_TABLE",
                "INCLUDES",
                "FORWARDS_TO",
                "READS_PARAM",
                "WRITES_PARAM",
                "PASSES_PARAM",
                "USES_CONFIG",
                "USES_TAGLIB",
                "RENDERS_INPUT",
                "READS_MODEL_ATTR",
                "READS_REQUEST_PARAM",
                "READS_SESSION_ATTR",
                "WRITES_MODEL_ATTR",
                "WRITES_REQUEST_ATTR",
                "WRITES_SESSION_ATTR",
                "READS_FIELD",
                "WRITES_FIELD",
                "CONTAINS",
                "LOADS_SCRIPT",
                "NAVIGATES_TO",
                "HANDLES_DOM_EVENT",
                "CALLS_HTTP",
                "INVOKES",
                "SCHEDULES",
                "TRIGGERS",
                "PUBLISHES_TO",
                "CONSUMES_FROM",
                "HAS_PARAM",
                "CALLS_COMMAND",
                "READS_COLUMN",
                "WRITES_COLUMN",
                "HAS_VARIANT",
                "GUARDED_BY",
                "MAPS_TO_COLUMN",
                "MATCHES",
                "WATCHES",
                "REQUIRES_CHANGE",
                "SUGGESTS_REVIEW",
                "REQUIRES_TEST",
                "EXPORTS_SYMBOL",
                "REFERENCES_SYMBOL")) {
            RelationType relationType = registry.require(relationName);

            assertEquals(relationName, relationType.name());
            assertTrue(relationType.mvp());
        }
    }

    @Test
    void registersBoundaryAndEnhancementRelationsFromDesign() {
        RelationKindRegistry registry = RelationKindRegistry.defaults();

        assertSame(RelationFamily.BOUNDARY, registry.require("CALLS_NATIVE").family());
        assertSame(RelationFamily.BOUNDARY, registry.require("HAS_NATIVE_BOUNDARY").family());
        assertSame(RelationFamily.BOUNDARY, registry.require("EXPORTS_SYMBOL").family());
        assertSame(RelationFamily.BOUNDARY, registry.require("REFERENCES_SYMBOL").family());
        assertSame(RelationFamily.CONFIGURATION, registry.require("CONFIGURES_PROPERTY").family());
        assertSame(RelationFamily.PLANNING, registry.require("SUMMARIZES").family());
        assertSame(RelationFamily.WORKFLOW, registry.require("COMMENTS_ON").family());
        assertSame(RelationFamily.WORKFLOW, registry.require("VIOLATES_POLICY").family());
        assertSame(RelationFamily.WORKFLOW, registry.require("SUPPRESSED_BY").family());
        assertSame(RelationFamily.WORKFLOW, registry.require("EXPORTED_AS").family());
        assertSame(RelationFamily.CANDIDATE, registry.require("REFLECTS_TO").family());
        assertSame(RelationFamily.AI_ASSISTED_CANDIDATE, registry.require("AI_SUGGESTS_RELATION").family());
    }

    @Test
    void registersRemainingDesignLevelRelations() {
        RelationKindRegistry registry = RelationKindRegistry.defaults();

        for (String relationName : List.of(
                "BINDS_PARAM",
                "RETURNS",
                "CHANGED_IN",
                "IMPACTS",
                "COVERED_BY",
                "HAS_DIAGNOSTIC",
                "CONFIRMED_SCOPE",
                "NOTIFIES",
                "ACKNOWLEDGES")) {
            assertEquals(relationName, registry.require(relationName).name());
        }
    }

    @Test
    void rejectsUnknownRelations() {
        RelationKindRegistry registry = RelationKindRegistry.defaults();

        assertThrows(IllegalArgumentException.class, () -> registry.require("CALLS_MAYBE"));
    }
}
