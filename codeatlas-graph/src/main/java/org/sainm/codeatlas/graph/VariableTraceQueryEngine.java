package org.sainm.codeatlas.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.sainm.codeatlas.facts.CurrentFactReport;
import org.sainm.codeatlas.facts.FactRecord;

public final class VariableTraceQueryEngine {
    private VariableTraceQueryEngine() {
    }

    public static VariableTraceQueryEngine defaults() {
        return new VariableTraceQueryEngine();
    }

    public VariableTraceReport traceSources(CurrentFactReport report, String paramSlotId, int maxDepth, int maxPaths) {
        List<ImpactPath> paths = reverseFlowPaths(report, paramSlotId, maxDepth, maxPaths);
        return new VariableTraceReport("source", paramSlotId, paths, List.of(), paths, paths.size() >= normalizedLimit(maxPaths));
    }

    public VariableTraceReport traceSinks(CurrentFactReport report, String paramSlotId, int maxDepth, int maxPaths) {
        List<ImpactPath> paths = forwardFlowPaths(report, paramSlotId, maxDepth, maxPaths);
        return new VariableTraceReport("sink", paramSlotId, List.of(), paths, paths, paths.size() >= normalizedLimit(maxPaths));
    }

    public VariableTraceReport traceCombined(CurrentFactReport report, String identityId, int maxDepth, int maxPaths) {
        List<ImpactPath> sourcePaths = reverseFlowPaths(report, identityId, maxDepth, maxPaths);
        List<ImpactPath> sinkPaths = forwardFlowPaths(report, identityId, maxDepth, maxPaths);
        List<ImpactPath> combined = new ArrayList<>();
        combined.addAll(sourcePaths);
        combined.addAll(sinkPaths);
        return new VariableTraceReport(
                "combined",
                identityId,
                sourcePaths,
                sinkPaths,
                combined,
                sourcePaths.size() >= normalizedLimit(maxPaths) || sinkPaths.size() >= normalizedLimit(maxPaths));
    }

    private static List<ImpactPath> reverseFlowPaths(
            CurrentFactReport report,
            String startIdentityId,
            int maxDepth,
            int maxPaths) {
        return traverse(adjacency(report, false), startIdentityId, maxDepth, maxPaths, false);
    }

    private static List<ImpactPath> forwardFlowPaths(
            CurrentFactReport report,
            String startIdentityId,
            int maxDepth,
            int maxPaths) {
        Map<String, List<String>> graph = adjacency(report, true);
        addDerivedMethodSinkEdges(report, graph);
        addStartParamSinkEdges(report, graph, startIdentityId);
        return traverse(graph, startIdentityId, maxDepth, maxPaths, true);
    }

    private static Map<String, List<String>> adjacency(CurrentFactReport report, boolean forward) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (FactRecord fact : report.facts()) {
            if (!isVariableTraceEdge(fact)) {
                continue;
            }
            String source = forward ? fact.sourceIdentityId() : fact.targetIdentityId();
            String target = forward ? fact.targetIdentityId() : fact.sourceIdentityId();
            result.computeIfAbsent(source, ignored -> new ArrayList<>()).add(target);
        }
        return result;
    }

    private static void addDerivedMethodSinkEdges(CurrentFactReport report, Map<String, List<String>> graph) {
        for (FactRecord fact : report.facts()) {
            if (!fact.relationType().name().equals("BINDS_TO") || !fact.sourceIdentityId().startsWith("method://")) {
                continue;
            }
            String methodId = fact.sourceIdentityId();
            String sqlId = fact.targetIdentityId();
            for (String paramSlot : graph.keySet().stream().filter(id -> ownerMethodId(id).equals(methodId)).toList()) {
                graph.computeIfAbsent(paramSlot, ignored -> new ArrayList<>()).add(sqlId);
            }
        }
        for (FactRecord fact : report.facts()) {
            if (fact.relationType().name().equals("HAS_PARAM")
                    || fact.relationType().name().equals("READS_TABLE")
                    || fact.relationType().name().equals("WRITES_TABLE")) {
                graph.computeIfAbsent(fact.sourceIdentityId(), ignored -> new ArrayList<>()).add(fact.targetIdentityId());
            }
        }
    }

    private static void addStartParamSinkEdges(
            CurrentFactReport report,
            Map<String, List<String>> graph,
            String startIdentityId) {
        String methodId = ownerMethodId(startIdentityId);
        if (methodId.isBlank()) {
            return;
        }
        for (FactRecord fact : report.facts()) {
            if (fact.relationType().name().equals("BINDS_TO") && fact.sourceIdentityId().equals(methodId)) {
                graph.computeIfAbsent(startIdentityId, ignored -> new ArrayList<>()).add(fact.targetIdentityId());
            }
        }
    }

    private static List<ImpactPath> traverse(
            Map<String, List<String>> graph,
            String startIdentityId,
            int maxDepth,
            int maxPaths,
            boolean stopAtSink) {
        int limit = normalizedLimit(maxPaths);
        Queue<List<String>> queue = new ArrayDeque<>();
        queue.add(List.of(startIdentityId));
        List<ImpactPath> paths = new ArrayList<>();
        while (!queue.isEmpty() && paths.size() < limit) {
            List<String> path = queue.remove();
            if (path.size() - 1 >= maxDepth) {
                continue;
            }
            for (String next : graph.getOrDefault(path.getLast(), List.of())) {
                if (path.contains(next)) {
                    continue;
                }
                List<String> nextPath = append(path, next);
                if (!stopAtSink || isSink(next)) {
                    paths.add(new ImpactPath(nextPath));
                    if (paths.size() >= limit) {
                        break;
                    }
                }
                queue.add(nextPath);
            }
        }
        return paths;
    }

    private static boolean isVariableTraceEdge(FactRecord fact) {
        return fact.relationType().name().equals("BINDS_TO")
                || fact.relationType().name().equals("PASSES_PARAM");
    }

    private static boolean isSink(String identityId) {
        return identityId.startsWith("sql-param://")
                || identityId.startsWith("db-table://")
                || identityId.startsWith("db-column://")
                || identityId.startsWith("sql-statement://");
    }

    private static String ownerMethodId(String paramSlotId) {
        if (!paramSlotId.startsWith("param-slot://")) {
            return "";
        }
        int suffix = paramSlotId.indexOf(":param[");
        if (suffix < 0) {
            return "";
        }
        String owner = paramSlotId.substring("param-slot://".length(), suffix);
        int sourceRootEnd = owner.indexOf("/src/main/java/");
        if (sourceRootEnd >= 0) {
            owner = owner.substring(0, sourceRootEnd) + "/src/main/java/"
                    + owner.substring(sourceRootEnd + "/src/main/java/".length());
        }
        return "method://" + owner;
    }

    private static List<String> append(List<String> path, String identityId) {
        List<String> result = new ArrayList<>(path);
        result.add(identityId);
        return List.copyOf(result);
    }

    private static int normalizedLimit(int maxPaths) {
        return maxPaths <= 0 ? 100 : maxPaths;
    }
}
