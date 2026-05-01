package org.sainm.codeatlas.graph.impact;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeepImpactReportSupplementer {
    public ImpactReport supplement(
        ImpactReport baseReport,
        List<ImpactPath> supplementalPaths,
        List<ImpactEvidence> supplementalEvidence
    ) {
        if (baseReport == null) {
            throw new IllegalArgumentException("baseReport is required");
        }
        Map<String, ImpactPath> paths = new LinkedHashMap<>();
        baseReport.paths().forEach(path -> paths.put(pathKey(path), path));
        if (supplementalPaths != null) {
            supplementalPaths.forEach(path -> paths.putIfAbsent(pathKey(path), path));
        }
        Map<String, ImpactEvidence> evidence = new LinkedHashMap<>();
        baseReport.evidenceList().forEach(item -> evidence.put(evidenceKey(item), item));
        if (supplementalEvidence != null) {
            supplementalEvidence.forEach(item -> evidence.putIfAbsent(evidenceKey(item), item));
        }
        boolean truncated = baseReport.truncated() || paths.values().stream().anyMatch(ImpactPath::truncated);
        return new ImpactReport(
            baseReport.reportId(),
            baseReport.projectId(),
            baseReport.snapshotId(),
            baseReport.changeSetId(),
            ReportDepth.DEEP,
            baseReport.createdAt(),
            List.copyOf(paths.values()),
            List.copyOf(evidence.values()),
            truncated
        );
    }

    private String pathKey(ImpactPath path) {
        StringBuilder builder = new StringBuilder();
        builder.append(path.entrypoint().value()).append('|')
            .append(path.changedSymbol().value()).append('|');
        for (ImpactPathStep step : path.steps()) {
            builder.append(step.incomingRelation()).append("->")
                .append(step.symbolId().value()).append('|');
        }
        return builder.toString();
    }

    private String evidenceKey(ImpactEvidence evidence) {
        return evidence.sourceType()
            + "|"
            + evidence.filePath()
            + "|"
            + evidence.lineNumber()
            + "|"
            + evidence.evidenceType()
            + "|"
            + evidence.snippet()
            + "|"
            + evidence.confidence();
    }
}
