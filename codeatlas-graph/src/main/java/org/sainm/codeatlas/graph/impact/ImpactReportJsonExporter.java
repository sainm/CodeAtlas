package org.sainm.codeatlas.graph.impact;

import org.sainm.codeatlas.graph.model.EvidenceKey;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

public final class ImpactReportJsonExporter {
    public String export(ImpactReport report) {
        return """
            {
              "reportId": "%s",
              "projectId": "%s",
              "snapshotId": "%s",
              "changeSetId": "%s",
              "depth": "%s",
              "createdAt": "%s",
              "truncated": %s,
              "affectedSymbols": [%s],
              "paths": [%s],
              "evidenceList": [%s]
            }
            """.formatted(
            escape(report.reportId()),
            escape(report.projectId()),
            escape(report.snapshotId()),
            escape(report.changeSetId()),
            report.depth(),
            DateTimeFormatter.ISO_INSTANT.format(report.createdAt()),
            report.truncated(),
            report.affectedSymbols().stream().map(this::affectedSymbol).collect(Collectors.joining(",")),
            report.paths().stream().map(this::path).collect(Collectors.joining(",")),
            report.evidenceList().stream().map(this::evidence).collect(Collectors.joining(","))
        ).strip();
    }

    private String affectedSymbol(ImpactAffectedSymbol symbol) {
        return """

                {
                  "category": "%s",
                  "symbolId": "%s",
                  "displayName": "%s"
                }""".formatted(
            escape(symbol.category()),
            escape(symbol.symbolId().value()),
            escape(symbol.displayName())
        );
    }

    private String path(ImpactPath path) {
        return """

                {
                  "entrypoint": "%s",
                  "changedSymbol": "%s",
                  "confidence": "%s",
                  "sourceTypeList": [%s],
                  "riskLevel": "%s",
                  "reason": "%s",
                  "truncated": %s,
                  "path": [%s]
                }""".formatted(
            escape(path.entrypoint().value()),
            escape(path.changedSymbol().value()),
            path.confidence(),
            path.sourceTypes().stream()
                .map(sourceType -> "\"" + sourceType.name() + "\"")
                .collect(Collectors.joining(",")),
            path.riskLevel(),
            escape(path.reason()),
            path.truncated(),
            path.steps().stream().map(this::step).collect(Collectors.joining(","))
        );
    }

    private String step(ImpactPathStep step) {
        String relation = step.incomingRelation() == null ? "ENTRY" : step.incomingRelation().name();
        return """

                    {
                      "symbolId": "%s",
                      "incomingRelation": "%s",
                      "sourceType": "%s",
                      "analysisLayer": "%s",
                      "confidence": "%s",
                      "evidenceKeys": [%s]
                    }""".formatted(
            escape(step.symbolId().value()),
            relation,
            step.sourceType(),
            analysisLayer(step),
            step.confidence(),
            step.evidenceKeys().stream().map(this::evidenceKey).collect(Collectors.joining(","))
        );
    }

    private String analysisLayer(ImpactPathStep step) {
        return step.sourceType() == org.sainm.codeatlas.graph.model.SourceType.TAI_E
            ? "DEEP_SUPPLEMENT"
            : "STATIC_FACT";
    }

    private String evidenceKey(EvidenceKey evidenceKey) {
        return """

                        {
                          "sourceType": "%s",
                          "analyzer": "%s",
                          "path": "%s",
                          "lineStart": %d,
                          "lineEnd": %d,
                          "localPath": "%s"
                        }""".formatted(
            evidenceKey.sourceType(),
            escape(evidenceKey.analyzer()),
            escape(evidenceKey.path()),
            evidenceKey.lineStart(),
            evidenceKey.lineEnd(),
            escape(evidenceKey.localPath())
        );
    }

    private String evidence(ImpactEvidence evidence) {
        return """

                {
                  "filePath": "%s",
                  "lineNumber": %d,
                  "evidenceType": "%s",
                  "snippet": "%s",
                  "sourceType": "%s",
                  "confidence": "%s"
                }""".formatted(
            escape(evidence.filePath()),
            evidence.lineNumber(),
            escape(evidence.evidenceType()),
            escape(evidence.snippet()),
            evidence.sourceType(),
            evidence.confidence()
        );
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }
}
