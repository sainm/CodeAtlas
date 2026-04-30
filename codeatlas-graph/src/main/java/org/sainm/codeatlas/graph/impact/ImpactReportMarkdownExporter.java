package org.sainm.codeatlas.graph.impact;

import java.util.StringJoiner;

public final class ImpactReportMarkdownExporter {
    public String export(ImpactReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Impact Report\n\n");
        builder.append("- Report: `").append(report.reportId()).append("`\n");
        builder.append("- Project: `").append(report.projectId()).append("`\n");
        builder.append("- Snapshot: `").append(report.snapshotId()).append("`\n");
        builder.append("- Change Set: `").append(report.changeSetId()).append("`\n");
        builder.append("- Depth: `").append(report.depth()).append("`\n");
        builder.append("- Truncated: `").append(report.truncated()).append("`\n\n");

        builder.append("## Affected Symbols\n\n");
        if (report.affectedSymbols().isEmpty()) {
            builder.append("No affected symbols found.\n\n");
        } else {
            builder.append("| Category | Symbol | Display |\n");
            builder.append("| --- | --- | --- |\n");
            for (ImpactAffectedSymbol symbol : report.affectedSymbols()) {
                builder.append("| `").append(symbol.category()).append("` | `")
                    .append(symbol.symbolId().value()).append("` | ")
                    .append(symbol.displayName()).append(" |\n");
            }
            builder.append("\n");
        }

        builder.append("## Paths\n\n");
        if (report.paths().isEmpty()) {
            builder.append("No impact paths found.\n\n");
        } else {
            for (int i = 0; i < report.paths().size(); i++) {
                ImpactPath path = report.paths().get(i);
                builder.append("### Path ").append(i + 1).append("\n\n");
                builder.append("- Entrypoint: `").append(path.entrypoint().value()).append("`\n");
                builder.append("- Changed Symbol: `").append(path.changedSymbol().value()).append("`\n");
                builder.append("- Confidence: `").append(path.confidence()).append("`\n");
                builder.append("- Risk: `").append(path.riskLevel()).append("`\n");
                builder.append("- Reason: ").append(path.reason()).append("\n\n");
                builder.append(pathSteps(path)).append("\n\n");
            }
        }

        builder.append("## Evidence\n\n");
        if (report.evidenceList().isEmpty()) {
            builder.append("No evidence attached.\n");
        } else {
            builder.append("| File | Line | Type | Source | Confidence |\n");
            builder.append("| --- | ---: | --- | --- | --- |\n");
            for (ImpactEvidence evidence : report.evidenceList()) {
                builder.append("| `").append(evidence.filePath()).append("` | ")
                    .append(evidence.lineNumber()).append(" | `")
                    .append(evidence.evidenceType()).append("` | `")
                    .append(evidence.sourceType()).append("` | `")
                    .append(evidence.confidence()).append("` |\n");
            }
        }
        return builder.toString();
    }

    private String pathSteps(ImpactPath path) {
        StringJoiner joiner = new StringJoiner("\n");
        for (ImpactPathStep step : path.steps()) {
            String relation = step.incomingRelation() == null ? "ENTRY" : step.incomingRelation().name();
            joiner.add("- `" + relation + "` `" + step.symbolId().value() + "`"
                + " [" + step.confidence() + ", " + step.sourceType() + ", evidence=" + step.evidenceKeys().size() + "]");
        }
        return joiner.toString();
    }
}
