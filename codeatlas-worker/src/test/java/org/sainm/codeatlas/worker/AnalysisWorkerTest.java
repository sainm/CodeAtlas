package org.sainm.codeatlas.worker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.sainm.codeatlas.analyzers.CodeAtlasProjectAnalyzer;
import org.sainm.codeatlas.graph.neo4j.RecordingCypherExecutor;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalysisWorkerTest {
    @TempDir
    Path tempDir;

    @Test
    void analyzesProjectAndWritesCypherStatements() throws Exception {
        write("src/main/java/com/acme/UserAction.java", """
            package com.acme;
            class UserAction {
              void execute() {}
            }
            """);
        write("src/main/webapp/user/edit.jsp", """
            <html:form action="/user/save.do">
              <html:text property="name"/>
            </html:form>
            """);

        RecordingCypherExecutor executor = new RecordingCypherExecutor();
        AnalysisWorkerResult result = new AnalysisWorker(new CodeAtlasProjectAnalyzer()).run(
            new AnalysisWorkerJob("shop", "shop", "_root", "snapshot-1", "run-1", "project", tempDir),
            executor
        );

        assertTrue(result.nodeCount() > 0);
        assertTrue(result.factCount() > 0);
        assertTrue(executor.statements().stream().anyMatch(statement -> statement.cypher().contains("CREATE CONSTRAINT")));
        assertTrue(executor.statements().stream().anyMatch(statement -> statement.cypher().contains("MERGE (n:Node")));
    }

    @Test
    void analyzesDiscoveredMavenModules() throws Exception {
        write("pom.xml", """
            <project>
              <modules>
                <module>web</module>
              </modules>
            </project>
            """);
        write("web/src/main/java/com/acme/UserAction.java", """
            package com.acme;
            class UserAction {
              void execute() {}
            }
            """);

        RecordingCypherExecutor executor = new RecordingCypherExecutor();
        AnalysisWorkerResult result = new AnalysisWorker(new CodeAtlasProjectAnalyzer()).run(
            new AnalysisWorkerJob("shop", "shop", "_root", "snapshot-1", "run-1", "project", tempDir),
            executor
        );

        assertTrue(result.nodeCount() > 0);
        assertTrue(executor.statements().stream().anyMatch(statement -> statement.parameters().toString().contains("web")));
    }

    private void write(String relativePath, String content) throws Exception {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
