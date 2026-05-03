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
                "CREATE CONSTRAINT codeatlas_method_symbol_id_snapshot_id IF NOT EXISTS FOR (n:Method) REQUIRE (n.symbolId, n.snapshotId) IS UNIQUE"));
        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_sql_parameter_flow_id_snapshot_id IF NOT EXISTS FOR (n:SqlParameter) REQUIRE (n.flowId, n.snapshotId) IS UNIQUE"));
        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_param_slot_flow_id_snapshot_id IF NOT EXISTS FOR (n:ParamSlot) REQUIRE (n.flowId, n.snapshotId) IS UNIQUE"));
        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_method_summary_flow_id_snapshot_id IF NOT EXISTS FOR (n:MethodSummary) REQUIRE (n.flowId, n.snapshotId) IS UNIQUE"));
        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_reflection_candidate_flow_id_snapshot_id IF NOT EXISTS FOR (n:ReflectionCandidate) REQUIRE (n.flowId, n.snapshotId) IS UNIQUE"));
        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_data_source_symbol_id_snapshot_id IF NOT EXISTS FOR (n:DataSource) REQUIRE (n.symbolId, n.snapshotId) IS UNIQUE"));
        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_db_index_symbol_id_snapshot_id IF NOT EXISTS FOR (n:DbIndex) REQUIRE (n.symbolId, n.snapshotId) IS UNIQUE"));
        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_db_constraint_symbol_id_snapshot_id IF NOT EXISTS FOR (n:DbConstraint) REQUIRE (n.symbolId, n.snapshotId) IS UNIQUE"));
        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_db_view_symbol_id_snapshot_id IF NOT EXISTS FOR (n:DbView) REQUIRE (n.symbolId, n.snapshotId) IS UNIQUE"));
        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_synthetic_symbol_symbol_id_snapshot_id IF NOT EXISTS FOR (n:SyntheticSymbol) REQUIRE (n.symbolId, n.snapshotId) IS UNIQUE"));
        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_feature_seed_artifact_id_snapshot_id IF NOT EXISTS FOR (n:FeatureSeed) REQUIRE (n.artifactId, n.snapshotId) IS UNIQUE"));
        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_change_plan_artifact_id_snapshot_id IF NOT EXISTS FOR (n:ChangePlan) REQUIRE (n.artifactId, n.snapshotId) IS UNIQUE"));
        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_import_review_artifact_id_snapshot_id IF NOT EXISTS FOR (n:ImportReview) REQUIRE (n.artifactId, n.snapshotId) IS UNIQUE"));
        assertTrue(statements.contains(
                "CREATE CONSTRAINT codeatlas_analysis_scope_decision_artifact_id_snapshot_id IF NOT EXISTS FOR (n:AnalysisScopeDecision) REQUIRE (n.artifactId, n.snapshotId) IS UNIQUE"));
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
                "MERGE (n:CodeAtlasNode:Project {symbolId: $symbolId, snapshotId: $snapshotId}) SET n.projectId = $projectId, n.kind = $kind",
                schema.mergeNodeCypher("Project"));
        assertEquals(
                "MATCH (n:CodeAtlasNode:DbTable {symbolId: $symbolId, snapshotId: $snapshotId}) WHERE n.projectId = $projectId RETURN n",
                schema.findNodeCypher("DbTable"));
        assertEquals(
                "MATCH (n:CodeAtlasNode:DbView {symbolId: $symbolId, snapshotId: $snapshotId}) WHERE n.projectId = $projectId RETURN n",
                schema.findNodeCypher("DbView"));
        assertEquals(
                "MATCH (n:CodeAtlasNode:ParamSlot {flowId: $flowId, snapshotId: $snapshotId}) WHERE n.projectId = $projectId RETURN n",
                schema.findNodeCypher("ParamSlot"));
        assertEquals(
                "MATCH (n:CodeAtlasNode:ImportReview {artifactId: $artifactId, snapshotId: $snapshotId}) WHERE n.projectId = $projectId RETURN n",
                schema.findNodeCypher("ImportReview"));
    }
}
