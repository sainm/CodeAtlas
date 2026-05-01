package org.sainm.codeatlas.graph.flow;

import org.sainm.codeatlas.graph.model.EvidenceKey;
import java.util.List;
import java.util.stream.Collectors;

public final class GraphFlowJsonExporter {
    public String export(String projectId, String snapshotId, String startSymbolId, List<GraphFlowPath> paths) {
        return "{\"projectId\":\"" + escape(projectId)
            + "\",\"snapshotId\":\"" + escape(snapshotId)
            + "\",\"startSymbolId\":\"" + escape(startSymbolId)
            + "\",\"pathCount\":" + paths.size()
            + ",\"paths\":" + paths.stream().map(this::path).collect(Collectors.joining(",", "[", "]"))
            + "}";
    }

    private String path(GraphFlowPath path) {
        return "{\"start\":\"" + escape(path.start().value())
            + "\",\"endpoint\":\"" + escape(path.endpoint().value())
            + "\",\"confidence\":\"" + path.confidence()
            + "\",\"sourceTypeList\":" + path.sourceTypes().stream()
                .map(sourceType -> "\"" + sourceType.name() + "\"")
                .collect(Collectors.joining(",", "[", "]"))
            + ",\"truncated\":" + path.truncated()
            + ",\"path\":" + path.steps().stream().map(this::step).collect(Collectors.joining(",", "[", "]"))
            + "}";
    }

    private String step(GraphFlowStep step) {
        String relation = step.incomingRelation() == null ? "ENTRY" : step.incomingRelation().name();
        return "{\"symbolId\":\"" + escape(step.symbolId().value())
            + "\",\"incomingRelation\":\"" + relation
            + "\",\"qualifier\":\"" + escape(step.qualifier())
            + "\",\"sourceType\":\"" + step.sourceType()
            + "\",\"confidence\":\"" + step.confidence()
            + "\",\"evidenceKeys\":" + step.evidenceKeys().stream().map(this::evidenceKey).collect(Collectors.joining(",", "[", "]"))
            + "}";
    }

    private String evidenceKey(EvidenceKey evidenceKey) {
        return "{\"sourceType\":\"" + evidenceKey.sourceType()
            + "\",\"analyzer\":\"" + escape(evidenceKey.analyzer())
            + "\",\"path\":\"" + escape(evidenceKey.path())
            + "\",\"lineStart\":" + evidenceKey.lineStart()
            + ",\"lineEnd\":" + evidenceKey.lineEnd()
            + ",\"localPath\":\"" + escape(evidenceKey.localPath())
            + "\"}";
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
