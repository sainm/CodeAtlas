package org.sainm.codeatlas.graph.variable;

import org.sainm.codeatlas.graph.model.EvidenceKey;
import java.util.List;
import java.util.stream.Collectors;

public final class VariableTraceJsonExporter {
    public String export(String projectId, String snapshotId, String parameterSymbolId, List<VariableTracePath> paths) {
        return "{\"projectId\":\"" + escape(projectId)
            + "\",\"snapshotId\":\"" + escape(snapshotId)
            + "\",\"parameterSymbolId\":\"" + escape(parameterSymbolId)
            + "\",\"pathCount\":" + paths.size()
            + ",\"paths\":" + paths.stream().map(this::path).collect(Collectors.joining(",", "[", "]"))
            + "}";
    }

    private String path(VariableTracePath path) {
        return "{\"direction\":\"" + path.direction()
            + "\",\"directionLabel\":\"" + escape(directionLabel(path.direction()))
            + "\",\"parameter\":\"" + escape(path.parameter().value())
            + "\",\"parameterDisplayName\":\"" + escape(displayName(path.parameter()))
            + "\",\"endpoint\":\"" + escape(path.endpoint().value())
            + "\",\"endpointDisplayName\":\"" + escape(displayName(path.endpoint()))
            + "\",\"confidence\":\"" + path.confidence()
            + "\",\"sourceTypeList\":" + path.sourceTypes().stream()
                .map(sourceType -> "\"" + sourceType.name() + "\"")
                .collect(Collectors.joining(",", "[", "]"))
            + ",\"truncated\":" + path.truncated()
            + ",\"path\":" + path.steps().stream().map(this::step).collect(Collectors.joining(",", "[", "]"))
            + "}";
    }

    private String step(VariableTraceStep step) {
        String relation = step.incomingRelation() == null ? "ENTRY" : step.incomingRelation().name();
        return "{\"symbolId\":\"" + escape(step.symbolId().value())
            + "\",\"displayName\":\"" + escape(displayName(step.symbolId()))
            + "\",\"symbolKind\":\"" + step.symbolId().kind()
            + "\",\"symbolKindLabel\":\"" + escape(kindLabel(step.symbolId()))
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

    private String directionLabel(VariableTraceDirection direction) {
        return switch (direction) {
            case SOURCE -> "值从哪里来";
            case SINK -> "值去了哪里";
        };
    }

    private String displayName(org.sainm.codeatlas.graph.model.SymbolId symbolId) {
        return switch (symbolId.kind()) {
            case REQUEST_PARAMETER -> "输入参数 " + lastSegment(symbolId.ownerQualifiedName());
            case JSP_PAGE -> shortPath(symbolId.ownerQualifiedName());
            case JSP_FORM -> "页面表单 " + shortPath(symbolId.ownerQualifiedName()) + localSuffix(symbolId.localId());
            case JSP_INPUT -> "页面输入 " + inputName(symbolId.localId());
            case METHOD -> simpleClassName(symbolId.ownerQualifiedName()) + "." + symbolId.memberName();
            case CLASS, INTERFACE, ENUM, ANNOTATION -> simpleClassName(symbolId.ownerQualifiedName());
            case ACTION_PATH, API_ENDPOINT -> ensureLeadingSlash(symbolId.ownerQualifiedName());
            case SQL_STATEMENT -> symbolId.localId() == null ? "SQL 语句" : "SQL " + symbolId.localId();
            case DB_TABLE, DB_COLUMN -> lastSegment(symbolId.ownerQualifiedName());
            case CONFIG_KEY -> shortPath(symbolId.ownerQualifiedName()) + localSuffix(symbolId.localId());
            default -> symbolId.value();
        };
    }

    private String kindLabel(org.sainm.codeatlas.graph.model.SymbolId symbolId) {
        return switch (symbolId.kind()) {
            case REQUEST_PARAMETER -> "输入参数";
            case JSP_PAGE -> "页面";
            case JSP_FORM -> "页面表单";
            case JSP_INPUT -> "页面输入";
            case METHOD -> "Java 方法";
            case CLASS, INTERFACE, ENUM, ANNOTATION -> "Java 类";
            case ACTION_PATH -> "Struts Action";
            case API_ENDPOINT -> "接口";
            case SQL_STATEMENT -> "SQL";
            case DB_TABLE -> "数据表";
            case DB_COLUMN -> "字段";
            case CONFIG_KEY -> "配置";
            default -> symbolId.kind().name();
        };
    }

    private String inputName(String localId) {
        if (localId == null || localId.isBlank()) {
            return "-";
        }
        int index = localId.lastIndexOf("input:");
        if (index >= 0) {
            return localId.substring(index + "input:".length());
        }
        return localId;
    }

    private String localSuffix(String localId) {
        return localId == null || localId.isBlank() ? "" : " " + localId;
    }

    private String ensureLeadingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "/";
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    private String simpleClassName(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        int index = value.lastIndexOf('.');
        return index >= 0 ? value.substring(index + 1) : value;
    }

    private String shortPath(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String[] parts = value.replace('\\', '/').split("/");
        if (parts.length <= 3) {
            return value;
        }
        return parts[parts.length - 3] + "/" + parts[parts.length - 2] + "/" + parts[parts.length - 1];
    }

    private String lastSegment(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        int dot = normalized.lastIndexOf('.');
        int index = Math.max(slash, dot);
        return index >= 0 ? normalized.substring(index + 1) : normalized;
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
