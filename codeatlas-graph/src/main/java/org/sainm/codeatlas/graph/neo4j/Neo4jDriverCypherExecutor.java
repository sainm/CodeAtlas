package org.sainm.codeatlas.graph.neo4j;

import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import java.util.List;
import java.util.Map;

public final class Neo4jDriverCypherExecutor implements CypherQueryExecutor, AutoCloseable {
    private final Driver driver;
    private final String database;

    public Neo4jDriverCypherExecutor(Driver driver, String database) {
        this.driver = driver;
        this.database = database == null || database.isBlank() ? null : database.trim();
    }

    @Override
    public void execute(CypherStatement statement) {
        try (var session = database == null ? driver.session() : driver.session(SessionConfig.forDatabase(database))) {
            session.executeWrite(transaction -> {
                transaction.run(statement.cypher(), statement.parameters());
                return null;
            });
        }
    }

    @Override
    public List<Map<String, Object>> query(CypherStatement statement) {
        try (var session = database == null ? driver.session() : driver.session(SessionConfig.forDatabase(database))) {
            return session.executeRead(transaction -> transaction
                .run(statement.cypher(), statement.parameters())
                .list(record -> record.asMap(value -> value.asObject())));
        }
    }

    @Override
    public void close() {
        driver.close();
    }
}
