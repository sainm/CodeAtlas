package org.sainm.codeatlas.graph.neo4j;

import java.util.List;
import java.util.Map;

public final class Neo4jSchemaCypher {
    public List<CypherStatement> statements() {
        return List.of(
            statement("CREATE CONSTRAINT node_symbol_id_unique IF NOT EXISTS FOR (n:Node) REQUIRE n.symbolId IS UNIQUE"),
            statement("CREATE INDEX node_kind_idx IF NOT EXISTS FOR (n:Node) ON (n.kind)"),
            statement("CREATE INDEX node_project_module_idx IF NOT EXISTS FOR (n:Node) ON (n.projectKey, n.moduleKey)"),
            statement("CREATE INDEX node_path_idx IF NOT EXISTS FOR (n:Node) ON (n.normalizedPath)"),
            statement("CREATE INDEX relationship_scope_idx IF NOT EXISTS FOR ()-[r]-() ON (r.projectId, r.snapshotId, r.scopeKey)")
        );
    }

    private CypherStatement statement(String cypher) {
        return new CypherStatement(cypher, Map.of());
    }
}
