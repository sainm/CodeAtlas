package org.sainm.codeatlas.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sainm.codeatlas.graph.neo4j.RecordingCypherExecutor;

class TaiEAnalysisResultImporterTest {
    @Test
    void importsTaiECallEdgesIntoNeo4jFacts() {
        RecordingCypherExecutor executor = new RecordingCypherExecutor();
        TaiESignatureMapper mapper = new TaiESignatureMapper("shop", "_root", "bytecode");
        TaiEAnalysisResultImporter importer = new TaiEAnalysisResultImporter(
            "shop",
            "snapshot-1",
            "run-tai-e",
            "tai-e",
            mapper
        );

        TaiEAnalysisImportResult result = importer.importCallEdges(
            List.of(new TaiEMethodCallEdge(
                "<com.acme.UserAction: void execute()>",
                "<com.acme.UserService: void save(java.lang.String)>",
                "tai-e-output/callgraph.txt",
                12,
                "pta"
            )),
            executor
        );

        assertEquals(2, result.nodeCount());
        assertEquals(1, result.factCount());
        assertTrue(executor.statements().stream().anyMatch(statement -> statement.cypher().contains("MERGE (n:Node")));
        assertTrue(executor.statements().stream().anyMatch(statement -> statement.cypher().contains("MERGE (source)-[r:CALLS")));
        assertTrue(executor.statements().stream().anyMatch(statement -> statement.parameters().toString().contains("TAI_E")));
        assertTrue(executor.statements().stream().anyMatch(statement -> statement.parameters().toString().contains("pta")));
    }
}
