package org.sainm.codeatlas.graph.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.graph.model.RelationType;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class GraphSchemaTest {
    @Test
    void definesBaseLabelsWithoutRoleDuplication() {
        Set<String> labels = GraphSchema.labels().stream()
            .map(LabelSpec::label)
            .collect(Collectors.toSet());

        assertTrue(labels.contains("Node"));
        assertTrue(labels.contains("CodeType"));
        assertTrue(labels.contains("CodeMember"));
        assertTrue(labels.contains("JspArtifact"));
        assertTrue(labels.contains("DatabaseObject"));
    }

    @Test
    void allRelationshipTypesCarryEvidenceProperties() {
        assertEquals(RelationType.values().length, GraphSchema.relationships().size());

        RelationshipSpec calls = GraphSchema.relationships().stream()
            .filter(spec -> spec.type() == RelationType.CALLS)
            .findFirst()
            .orElseThrow();

        Set<String> propertyNames = calls.properties().stream()
            .map(PropertySpec::name)
            .collect(Collectors.toSet());

        assertTrue(propertyNames.contains("evidenceKey"));
        assertTrue(propertyNames.contains("confidence"));
        assertTrue(propertyNames.contains("sourceType"));
        assertTrue(propertyNames.contains("snapshotId"));
        assertTrue(propertyNames.contains("analysisRunId"));
        assertTrue(propertyNames.contains("scopeKey"));
    }

    @Test
    void definesSymbolIdUniquenessAndLookupIndexes() {
        assertTrue(GraphSchema.constraints().stream()
            .anyMatch(spec -> spec.unique()
                && spec.label().equals("Node")
                && spec.properties().equals(java.util.List.of("symbolId"))));

        assertTrue(GraphSchema.indexes().stream()
            .anyMatch(spec -> spec.name().equals("node_project_module_idx")));
    }
}
