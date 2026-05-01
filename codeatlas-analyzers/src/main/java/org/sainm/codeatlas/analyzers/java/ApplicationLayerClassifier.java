package org.sainm.codeatlas.analyzers.java;

import org.sainm.codeatlas.graph.model.NodeRole;

public final class ApplicationLayerClassifier {
    private static final ApplicationRoleRuleSet DEFAULT_RULES = ApplicationRoleRuleSet.legacyConventions();

    private ApplicationLayerClassifier() {
    }

    public static NodeRole roleForQualifiedName(String qualifiedName) {
        return DEFAULT_RULES.roleForQualifiedName(qualifiedName);
    }

    public static boolean isApplicationLayer(NodeRole role) {
        return role == NodeRole.STRUTS_ACTION
            || role == NodeRole.CONTROLLER
            || role == NodeRole.BUSINESS_LOGIC
            || role == NodeRole.SERVICE
            || role == NodeRole.DAO
            || role == NodeRole.MAPPER;
    }

    public static boolean isSupportLayer(NodeRole role) {
        return role == NodeRole.UTILITY || isApplicationLayer(role);
    }
}
