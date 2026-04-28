package org.sainm.codeatlas.graph.neo4j;

import java.util.List;
import java.util.Map;

public interface CypherQueryExecutor extends CypherExecutor {
    List<Map<String, Object>> query(CypherStatement statement);
}
