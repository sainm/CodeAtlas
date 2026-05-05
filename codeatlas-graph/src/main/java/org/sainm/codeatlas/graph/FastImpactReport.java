package org.sainm.codeatlas.graph;

import java.util.List;

public record FastImpactReport(
        String projectId,
        String snapshotId,
        List<String> changedSymbols,
        List<ImpactPath> paths,
        List<ImpactPathDetail> pathDetails,
        List<DbImpactQueryResult> dbImpacts,
        List<String> affectedSymbols,
        List<String> suggestedTests,
        boolean aiEnabled,
        boolean truncated) {
    public FastImpactReport(
            String projectId,
            String snapshotId,
            List<String> changedSymbols,
            List<ImpactPath> paths,
            List<DbImpactQueryResult> dbImpacts,
            boolean truncated) {
        this(projectId, snapshotId, changedSymbols, paths, List.of(), dbImpacts, List.of(), List.of(), false, truncated);
    }

    public FastImpactReport {
        requireNonBlank(projectId, "projectId");
        requireNonBlank(snapshotId, "snapshotId");
        changedSymbols = List.copyOf(changedSymbols == null ? List.of() : changedSymbols);
        paths = List.copyOf(paths == null ? List.of() : paths);
        pathDetails = List.copyOf(pathDetails == null ? List.of() : pathDetails);
        dbImpacts = List.copyOf(dbImpacts == null ? List.of() : dbImpacts);
        affectedSymbols = List.copyOf(affectedSymbols == null ? List.of() : affectedSymbols);
        suggestedTests = List.copyOf(suggestedTests == null ? List.of() : suggestedTests);
    }

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append('{');
        appendField(json, "projectId", projectId).append(',');
        appendField(json, "snapshotId", snapshotId).append(',');
        appendStringArray(json, "changedSymbols", changedSymbols).append(',');
        appendStringArray(json, "affectedSymbols", affectedSymbols).append(',');
        appendPaths(json).append(',');
        appendPathDetails(json).append(',');
        appendDbImpacts(json).append(',');
        appendStringArray(json, "suggestedTests", suggestedTests).append(',');
        json.append("\"aiEnabled\":").append(aiEnabled).append(',');
        json.append("\"truncated\":").append(truncated);
        json.append('}');
        return json.toString();
    }

    private StringBuilder appendPaths(StringBuilder json) {
        json.append("\"paths\":[");
        for (int i = 0; i < paths.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('{');
            appendStringArray(json, "identityIds", paths.get(i).identityIds()).append(',');
            json.append("\"depth\":").append(paths.get(i).depth());
            json.append('}');
        }
        return json.append(']');
    }

    private StringBuilder appendPathDetails(StringBuilder json) {
        json.append("\"pathDetails\":[");
        for (int i = 0; i < pathDetails.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            ImpactPathDetail detail = pathDetails.get(i);
            json.append('{');
            appendStringArray(json, "identityIds", detail.path().identityIds()).append(',');
            appendField(json, "risk", detail.risk()).append(',');
            appendField(json, "confidence", detail.confidence().name()).append(',');
            appendField(json, "sourceType", detail.sourceType().name()).append(',');
            appendStringArray(json, "evidenceKeys", detail.evidenceKeys());
            json.append('}');
        }
        return json.append(']');
    }

    private StringBuilder appendDbImpacts(StringBuilder json) {
        json.append("\"dbImpacts\":[");
        for (int i = 0; i < dbImpacts.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            DbImpactQueryResult impact = dbImpacts.get(i);
            json.append('{');
            appendField(json, "targetIdentityId", impact.targetIdentityId()).append(',');
            json.append("\"readCount\":").append(impact.readFacts().size()).append(',');
            json.append("\"writeCount\":").append(impact.writeFacts().size()).append(',');
            json.append("\"upstreamBindingCount\":").append(impact.upstreamBindingFacts().size());
            json.append('}');
        }
        return json.append(']');
    }

    private static StringBuilder appendStringArray(StringBuilder json, String name, List<String> values) {
        json.append('"').append(name).append("\":[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            appendString(json, values.get(i));
        }
        return json.append(']');
    }

    private static StringBuilder appendField(StringBuilder json, String name, String value) {
        json.append('"').append(name).append("\":");
        return appendString(json, value);
    }

    private static StringBuilder appendString(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> json.append("\\\\");
                case '"' -> json.append("\\\"");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> json.append(c);
            }
        }
        return json.append('"');
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
