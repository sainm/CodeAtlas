package org.sainm.codeatlas.worker;

import org.sainm.codeatlas.analyzers.CodeAtlasProjectAnalyzer;
import org.sainm.codeatlas.graph.neo4j.RecordingCypherExecutor;
import java.nio.file.Path;

public final class CodeAtlasWorker {
    private CodeAtlasWorker() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: CodeAtlasWorker <projectRoot> [projectId] [projectKey]");
            return;
        }
        String projectId = args.length > 1 ? args[1] : "local";
        String projectKey = args.length > 2 ? args[2] : "local";
        AnalysisWorkerJob job = new AnalysisWorkerJob(
            projectId,
            projectKey,
            "_root",
            "snapshot-local",
            "run-local",
            "project",
            Path.of(args[0])
        );
        RecordingCypherExecutor executor = new RecordingCypherExecutor();
        AnalysisWorkerResult result = new AnalysisWorker(new CodeAtlasProjectAnalyzer()).run(job, executor);
        System.out.println("Analyzed nodes=" + result.nodeCount()
            + ", facts=" + result.factCount()
            + ", cypherStatements=" + executor.statements().size());
    }
}
