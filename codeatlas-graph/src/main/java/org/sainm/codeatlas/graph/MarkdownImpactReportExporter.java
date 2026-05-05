package org.sainm.codeatlas.graph;

public final class MarkdownImpactReportExporter {
    private MarkdownImpactReportExporter() {
    }

    public static MarkdownImpactReportExporter defaults() {
        return new MarkdownImpactReportExporter();
    }

    public String export(FastImpactReport report) {
        if (report == null) {
            throw new IllegalArgumentException("report is required");
        }
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Fast Impact Report\n\n");
        markdown.append("- Project: `").append(report.projectId()).append("`\n");
        markdown.append("- Snapshot: `").append(report.snapshotId()).append("`\n");
        markdown.append("- AI Enabled: `").append(report.aiEnabled()).append("`\n");
        markdown.append("- Truncated: `").append(report.truncated()).append("`\n\n");
        markdown.append("## Changed Symbols\n\n");
        if (report.changedSymbols().isEmpty()) {
            markdown.append("- None\n");
        } else {
            report.changedSymbols().forEach(symbol -> markdown.append("- `").append(symbol).append("`\n"));
        }
        markdown.append("\n## Affected Symbols\n\n");
        if (report.affectedSymbols().isEmpty()) {
            markdown.append("- None\n");
        } else {
            report.affectedSymbols().forEach(symbol -> markdown.append("- `").append(symbol).append("`\n"));
        }
        markdown.append("\n## Impact Paths\n\n");
        if (report.paths().isEmpty()) {
            markdown.append("- None\n");
        } else {
            for (ImpactPath path : report.paths()) {
                markdown.append("- ");
                for (int i = 0; i < path.identityIds().size(); i++) {
                    if (i > 0) {
                        markdown.append(" -> ");
                    }
                    markdown.append('`').append(path.identityIds().get(i)).append('`');
                }
                markdown.append('\n');
            }
        }
        if (!report.pathDetails().isEmpty()) {
            markdown.append("\n## Path Evidence\n\n");
            for (ImpactPathDetail detail : report.pathDetails()) {
                markdown.append("- Risk `").append(detail.risk()).append("`, confidence `")
                        .append(detail.confidence()).append("`, source `")
                        .append(detail.sourceType()).append("`, evidence ")
                        .append(detail.evidenceKeys()).append('\n');
            }
        }
        markdown.append("\n## DB Impacts\n\n");
        if (report.dbImpacts().isEmpty()) {
            markdown.append("- None\n");
        } else {
            for (DbImpactQueryResult impact : report.dbImpacts()) {
                markdown.append("- `").append(impact.targetIdentityId()).append("`: ")
                        .append(impact.readFacts().size()).append(" read, ")
                        .append(impact.writeFacts().size()).append(" write, ")
                        .append(impact.upstreamBindingFacts().size()).append(" upstream bindings\n");
            }
        }
        markdown.append("\n## Suggested Tests\n\n");
        if (report.suggestedTests().isEmpty()) {
            markdown.append("- None\n");
        } else {
            report.suggestedTests().forEach(test -> markdown.append("- ").append(test).append('\n'));
        }
        return markdown.toString();
    }
}
