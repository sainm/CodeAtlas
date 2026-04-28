package org.sainm.codeatlas.worker;

import org.sainm.codeatlas.analyzers.AnalyzerScope;
import org.sainm.codeatlas.analyzers.CodeAtlasProjectAnalyzer;
import org.sainm.codeatlas.analyzers.ProjectAnalysisResult;
import org.sainm.codeatlas.analyzers.project.ProjectLayoutAnalyzer;
import org.sainm.codeatlas.graph.neo4j.CypherExecutor;
import org.sainm.codeatlas.graph.neo4j.Neo4jCypherBuilder;
import org.sainm.codeatlas.graph.neo4j.Neo4jGraphWriter;
import org.sainm.codeatlas.graph.model.GraphFact;
import org.sainm.codeatlas.graph.model.GraphNode;
import java.util.ArrayList;
import java.util.List;

public final class AnalysisWorker {
    private final CodeAtlasProjectAnalyzer analyzer;
    private final ProjectLayoutAnalyzer layoutAnalyzer;

    public AnalysisWorker(CodeAtlasProjectAnalyzer analyzer) {
        this(analyzer, new ProjectLayoutAnalyzer());
    }

    public AnalysisWorker(CodeAtlasProjectAnalyzer analyzer, ProjectLayoutAnalyzer layoutAnalyzer) {
        this.analyzer = analyzer;
        this.layoutAnalyzer = layoutAnalyzer;
    }

    public AnalysisWorkerResult run(AnalysisWorkerJob job, CypherExecutor executor) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphFact> facts = new ArrayList<>();
        for (var module : layoutAnalyzer.analyze(job.projectId(), job.root()).modules()) {
            AnalyzerScope scope = new AnalyzerScope(
                job.projectId(),
                module.moduleKey(),
                job.snapshotId(),
                job.analysisRunId(),
                job.scopeKey() + ":" + module.moduleKey(),
                module.basePath()
            );
            ProjectAnalysisResult result = analyzer.analyze(scope, job.projectKey());
            nodes.addAll(result.nodes());
            facts.addAll(result.facts());
        }
        Neo4jGraphWriter writer = new Neo4jGraphWriter(executor, new Neo4jCypherBuilder());
        writer.applySchema();
        writer.upsertNodes(nodes);
        writer.upsertFacts(facts);
        return new AnalysisWorkerResult(nodes.size(), facts.size());
    }
}
