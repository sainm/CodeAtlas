package org.sainm.codeatlas.graph.schema;

import org.sainm.codeatlas.graph.model.NodeRole;
import org.sainm.codeatlas.graph.model.RelationType;
import java.util.Arrays;
import java.util.List;

public final class GraphSchema {
    private GraphSchema() {
    }

    public static List<LabelSpec> labels() {
        return List.of(
            nodeLabel(),
            roleLabel(NodeRole.PROJECT),
            roleLabel(NodeRole.MODULE),
            roleLabel(NodeRole.SOURCE_FILE),
            roleLabel(NodeRole.CODE_TYPE),
            roleLabel(NodeRole.CODE_MEMBER),
            roleLabel(NodeRole.WEB_ENTRYPOINT),
            roleLabel(NodeRole.JSP_ARTIFACT),
            roleLabel(NodeRole.REQUEST_PARAMETER),
            roleLabel(NodeRole.SQL_ARTIFACT),
            roleLabel(NodeRole.DATABASE_OBJECT)
        );
    }

    public static List<RelationshipSpec> relationships() {
        return Arrays.stream(RelationType.values())
            .map(type -> new RelationshipSpec(type, evidenceProperties()))
            .toList();
    }

    public static List<ConstraintSpec> constraints() {
        return List.of(
            new ConstraintSpec("node_symbol_id_unique", "Node", List.of("symbolId"), true),
            new ConstraintSpec("project_project_id_unique", "Project", List.of("projectId"), true)
        );
    }

    public static List<IndexSpec> indexes() {
        return List.of(
            new IndexSpec("node_kind_idx", "Node", List.of("kind")),
            new IndexSpec("node_project_module_idx", "Node", List.of("projectKey", "moduleKey")),
            new IndexSpec("node_path_idx", "Node", List.of("normalizedPath")),
            new IndexSpec("relationship_scope_idx", "RelationshipFact", List.of("projectId", "snapshotId", "scopeKey"))
        );
    }

    private static LabelSpec nodeLabel() {
        return new LabelSpec(
            "Node",
            List.of(
                new PropertySpec("symbolId", PropertyType.STRING, true),
                new PropertySpec("kind", PropertyType.STRING, true),
                new PropertySpec("projectKey", PropertyType.STRING, true),
                new PropertySpec("moduleKey", PropertyType.STRING, true),
                new PropertySpec("sourceRootKey", PropertyType.STRING, true),
                new PropertySpec("normalizedPath", PropertyType.STRING, false),
                new PropertySpec("displayName", PropertyType.STRING, false)
            )
        );
    }

    private static LabelSpec roleLabel(NodeRole role) {
        return new LabelSpec(toLabel(role), List.of());
    }

    private static String toLabel(NodeRole role) {
        String[] parts = role.name().toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private static List<PropertySpec> evidenceProperties() {
        return List.of(
            new PropertySpec("factKey", PropertyType.STRING, true),
            new PropertySpec("evidenceKey", PropertyType.STRING, true),
            new PropertySpec("projectId", PropertyType.STRING, true),
            new PropertySpec("snapshotId", PropertyType.STRING, true),
            new PropertySpec("analysisRunId", PropertyType.STRING, true),
            new PropertySpec("scopeKey", PropertyType.STRING, true),
            new PropertySpec("confidence", PropertyType.DOUBLE, true),
            new PropertySpec("sourceType", PropertyType.STRING, true),
            new PropertySpec("active", PropertyType.BOOLEAN, true),
            new PropertySpec("tombstone", PropertyType.BOOLEAN, true)
        );
    }
}
