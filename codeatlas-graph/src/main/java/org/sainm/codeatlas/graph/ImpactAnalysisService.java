package org.sainm.codeatlas.graph;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactStore;
import org.sainm.codeatlas.facts.InMemoryFactStore;

/**
 * High-level orchestration service that connects fact storage,
 * query engines, and traversal engines for impact analysis.
 *
 * <p>This is the primary entry point for impact analysis workflows.
 * It defaults to {@link InMemoryFactStore} and switches to Neo4j-backed
 * storage when a Neo4j dependency is available.
 */
public final class ImpactAnalysisService {
    private final FactStore factStore;
    private final CallerTraversalEngine callerEngine;
    private final DownstreamTraversalEngine downstreamEngine;
    private final DbImpactQueryEngine dbImpactEngine;
    private final VariableTraceQueryEngine variableTraceEngine;
    private final WebBackendFlowSearchEngine webFlowEngine;
    private final ReportImpactQueryEngine reportImpactEngine;

    private ImpactAnalysisService(FactStore factStore) {
        this.factStore = Objects.requireNonNull(factStore, "factStore");
        this.callerEngine = CallerTraversalEngine.defaults();
        this.downstreamEngine = DownstreamTraversalEngine.defaults();
        this.dbImpactEngine = DbImpactQueryEngine.defaults();
        this.variableTraceEngine = VariableTraceQueryEngine.defaults();
        this.webFlowEngine = WebBackendFlowSearchEngine.defaults();
        this.reportImpactEngine = ReportImpactQueryEngine.defaults();
    }

    public static ImpactAnalysisService defaults() {
        return new ImpactAnalysisService(InMemoryFactStore.defaults());
    }

    public static ImpactAnalysisService using(FactStore factStore) {
        return new ImpactAnalysisService(factStore);
    }

    public FactStore factStore() {
        return factStore;
    }

    public FastImpactReport analyzeDiff(
            String projectId,
            String snapshotId,
            List<String> changedSymbolIds,
            int maxDepth,
            int maxPaths) {
        CurrentFactReport report = factStore.report(projectId, snapshotId);
        Set<String> affectedSymbols = new LinkedHashSet<>();
        List<ImpactPath> allPaths = new ArrayList<>();
        boolean truncated = false;
        for (String changedId : changedSymbolIds) {
            CallerTraversalResult callers = callerEngine.findCallers(report, changedId, maxDepth, maxPaths);
            allPaths.addAll(callers.callerPaths());
            if (callers.truncated()) {
                truncated = true;
            }
            for (ImpactPath path : callers.callerPaths()) {
                affectedSymbols.add(path.endIdentityId());
            }
            DownstreamTraversalResult downstream = downstreamEngine.findDownstream(report, changedId, maxDepth, maxPaths);
            allPaths.addAll(downstream.downstreamPaths());
            if (downstream.truncated()) {
                truncated = true;
            }
            for (ImpactPath path : downstream.downstreamPaths()) {
                affectedSymbols.add(path.endIdentityId());
            }
        }
        List<DbImpactQueryResult> dbImpacts = new ArrayList<>();
        for (String affectedId : affectedSymbols) {
            if (affectedId.startsWith("db-table://")) {
                dbImpacts.add(dbImpactEngine.tableImpact(report, affectedId));
            } else if (affectedId.startsWith("db-column://")) {
                dbImpacts.add(dbImpactEngine.columnImpact(report, affectedId));
            }
        }
        List<ImpactPathDetail> pathDetails = new ArrayList<>();
        for (ImpactPath path : allPaths) {
            pathDetails.add(new ImpactPathDetail(
                    path,
                    "MEDIUM",
                    org.sainm.codeatlas.facts.Confidence.LIKELY,
                    org.sainm.codeatlas.facts.SourceType.IMPACT_FLOW,
                    List.of()));
        }
        return new FastImpactReport(
                projectId,
                snapshotId,
                changedSymbolIds,
                allPaths,
                pathDetails,
                dbImpacts,
                List.copyOf(affectedSymbols),
                List.of(),
                false,
                truncated);
    }

    public VariableTraceReport traceVariable(
            String projectId,
            String snapshotId,
            String identityId,
            int maxDepth,
            int maxPaths) {
        CurrentFactReport report = factStore.report(projectId, snapshotId);
        return variableTraceEngine.traceCombined(report, identityId, maxDepth, maxPaths);
    }

    public WebBackendFlowSearchResult findWebBackendFlow(
            String projectId,
            String snapshotId,
            String sourceIdentityId,
            int maxDepth,
            int maxPaths) {
        CurrentFactReport report = factStore.report(projectId, snapshotId);
        return webFlowEngine.findBackendFlows(report, sourceIdentityId, maxDepth, maxPaths);
    }

    public ReportImpactQueryEngine.ReportImpactResult findReportImpact(
            String projectId,
            String snapshotId,
            String dbColumnId) {
        CurrentFactReport report = factStore.report(projectId, snapshotId);
        return reportImpactEngine.findAffectedReports(report, dbColumnId);
    }

    public ArchitectureRuleChecker.ArchitectureCheckResult checkArchitecture(
            List<ImpactPath> paths,
            List<ArchitectureRule> rules) {
        ArchitectureRuleChecker checker = new ArchitectureRuleChecker(rules);
        return checker.check(paths);
    }

    /**
     * Enhances test recommendations with historical risk, ownership,
     * and change frequency signals from the given context.
     */
    public List<String> prioritizeTests(
            List<String> suggestedTests,
            List<ImpactPath> paths,
            TestRecommendationContext context) {
        if (context == null) {
            return List.copyOf(suggestedTests);
        }
        return context.prioritizeTests(suggestedTests, paths);
    }

    /**
     * Runs a full evaluation against a labeled sample set.
     */
    public EvaluationSampleSet.EvaluationResult evaluate(
            EvaluationSampleSet sampleSet,
            List<ImpactPath> reportedPaths) {
        return sampleSet.evaluate(reportedPaths);
    }
}
