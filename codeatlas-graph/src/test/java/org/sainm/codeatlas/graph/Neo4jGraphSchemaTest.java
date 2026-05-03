package org.sainm.codeatlas.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class Neo4jGraphSchemaTest {
    @Test
    void createsUniqueIdentityConstraintsForRegisteredNodes() {
        Neo4jGraphSchema schema = Neo4jGraphSchema.defaults();

        List<String> statements = schema.cypherStatements();

        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_method_symbol_id IF NOT EXISTS FOR (n:Method) REQUIRE n.symbolId IS UNIQUE"));
        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_sql_parameter_flow_id IF NOT EXISTS FOR (n:SqlParameter) REQUIRE n.flowId IS UNIQUE"));
        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_feature_seed_artifact_id IF NOT EXISTS FOR (n:FeatureSeed) REQUIRE n.artifactId IS UNIQUE"));
        assertEquals(GraphNodeTypeRegistry.defaults().all().size(), schema.identityConstraints().size());
    }

    @Test
    void createsCommonQueryIndexesWithoutPluginDependencies() {
        Neo4jGraphSchema schema = Neo4jGraphSchema.defaults();

        assertTrue(schema.cypherStatements().contains(
                "CREATE INDEX codeatlas_node_project_snapshot IF NOT EXISTS FOR (n:CodeAtlasNode) ON (n.projectId, n.snapshotId)"));
        assertTrue(schema.cypherStatements().contains(
                "CREATE INDEX codeatlas_node_kind IF NOT EXISTS FOR (n:CodeAtlasNode) ON (n.kind)"));
        assertTrue(schema.cypherStatements().contains(
                "CREATE INDEX codeatlas_fact_active_scope IF NOT EXISTS FOR ()-[r:CODEATLAS_FACT]-() ON (r.projectId, r.snapshotId, r.relationType, r.active)"));
    }

    @Test
    void providesParameterizedSampleStatementsForAcceptanceNodes() {
        Neo4jGraphSchema schema = Neo4jGraphSchema.defaults();

        assertEquals(
                "MERGE (n:CodeAtlasNode:Project {symbolId: $symbolId}) SET n.projectId = $projectId, n.snapshotId = $snapshotId, n.kind = $kind",
                schema.mergeNodeCypher("Project"));
        assertEquals(
                "MATCH (n:CodeAtlasNode:DbTable {symbolId: $symbolId}) WHERE n.projectId = $projectId AND n.snapshotId = $snapshotId RETURN n",
                schema.findNodeCypher("DbTable"));
    }
}
