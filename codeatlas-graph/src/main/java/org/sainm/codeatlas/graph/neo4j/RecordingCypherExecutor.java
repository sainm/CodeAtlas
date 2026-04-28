package org.sainm.codeatlas.graph.neo4j;

import java.util.ArrayList;
import java.util.List;

public final class RecordingCypherExecutor implements CypherExecutor {
    private final List<CypherStatement> statements = new ArrayList<>();

    @Override
    public void execute(CypherStatement statement) {
        statements.add(statement);
    }

    public List<CypherStatement> statements() {
        return List.copyOf(statements);
    }
}
