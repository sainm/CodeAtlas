package org.sainm.codeatlas.graph;

import java.util.List;

import org.sainm.codeatlas.facts.CurrentFactReport;

public final class WebBackendFlowSearchEngine {
    private WebBackendFlowSearchEngine() {
    }

    public static WebBackendFlowSearchEngine defaults() {
        return new WebBackendFlowSearchEngine();
    }

    public WebBackendFlowSearchResult findBackendFlows(
            CurrentFactReport report,
            String sourceIdentityId,
            int maxDepth,
            int maxPaths) {
        DownstreamTraversalResult downstream = DownstreamTraversalEngine.defaults()
                .findDownstream(report, sourceIdentityId, maxDepth, maxPaths);
        List<ImpactPath> backendPaths = downstream.downstreamPaths().stream()
                .filter(path -> isBackendSink(path.endIdentityId()))
                .toList();
        return new WebBackendFlowSearchResult(sourceIdentityId, backendPaths, downstream.truncated());
    }

    private static boolean isBackendSink(String identityId) {
        return identityId.startsWith("sql-statement://")
                || identityId.startsWith("db-table://")
                || identityId.startsWith("db-column://");
    }
}
