package org.sainm.codeatlas.facts;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RelationKindRegistry {
    private static final RelationKindRegistry DEFAULT = createDefault();

    private final Map<String, RelationType> relations;

    private RelationKindRegistry(Map<String, RelationType> relations) {
        this.relations = Map.copyOf(relations);
    }

    public static RelationKindRegistry defaults() {
        return DEFAULT;
    }

    public RelationType require(String name) {
        RelationType relation = relations.get(name);
        if (relation == null) {
            throw new IllegalArgumentException("Unknown relation kind: " + name);
        }
        return relation;
    }

    public boolean contains(String name) {
        return relations.containsKey(name);
    }

    public Collection<RelationType> all() {
        return relations.values();
    }

    private static RelationKindRegistry createDefault() {
        Map<String, RelationType> relations = new LinkedHashMap<>();
        registerMvpRelations(relations);
        registerEnhancedRelations(relations);
        return new RelationKindRegistry(relations);
    }

    private static void registerMvpRelations(Map<String, RelationType> relations) {
        register(relations, "DECLARES", RelationFamily.STRUCTURE, true);
        register(relations, "CALLS", RelationFamily.CALL, true);
        register(relations, "ROUTES_TO", RelationFamily.CALL, true);
        register(relations, "SUBMITS_TO", RelationFamily.WEB, true);
        register(relations, "BINDS_TO", RelationFamily.DATA, true);
        register(relations, "BINDS_PARAM", RelationFamily.FLOW, true);
        register(relations, "READS_TABLE", RelationFamily.DATABASE, true);
        register(relations, "WRITES_TABLE", RelationFamily.DATABASE, true);
        register(relations, "INCLUDES", RelationFamily.STRUCTURE, true);
        register(relations, "FORWARDS_TO", RelationFamily.WEB, true);
        register(relations, "READS_PARAM", RelationFamily.FLOW, true);
        register(relations, "WRITES_PARAM", RelationFamily.FLOW, true);
        register(relations, "PASSES_PARAM", RelationFamily.FLOW, true);
        register(relations, "USES_CONFIG", RelationFamily.CONFIGURATION, true);
        register(relations, "USES_TAGLIB", RelationFamily.WEB, true);
        register(relations, "RENDERS_INPUT", RelationFamily.WEB, true);
        register(relations, "RETURNS", RelationFamily.FLOW, true);
        register(relations, "READS_MODEL_ATTR", RelationFamily.FLOW, true);
        register(relations, "READS_REQUEST_PARAM", RelationFamily.FLOW, true);
        register(relations, "READS_SESSION_ATTR", RelationFamily.FLOW, true);
        register(relations, "WRITES_MODEL_ATTR", RelationFamily.FLOW, true);
        register(relations, "WRITES_REQUEST_ATTR", RelationFamily.FLOW, true);
        register(relations, "WRITES_SESSION_ATTR", RelationFamily.FLOW, true);
        register(relations, "READS_FIELD", RelationFamily.DATA, true);
        register(relations, "WRITES_FIELD", RelationFamily.DATA, true);
        register(relations, "CONTAINS", RelationFamily.STRUCTURE, true);
        register(relations, "LOADS_SCRIPT", RelationFamily.WEB, true);
        register(relations, "NAVIGATES_TO", RelationFamily.WEB, true);
        register(relations, "HANDLES_DOM_EVENT", RelationFamily.WEB, true);
        register(relations, "CALLS_HTTP", RelationFamily.WEB, true);
        register(relations, "INVOKES", RelationFamily.CALL, true);
        register(relations, "SCHEDULES", RelationFamily.ASYNC, true);
        register(relations, "TRIGGERS", RelationFamily.ASYNC, true);
        register(relations, "PUBLISHES_TO", RelationFamily.ASYNC, true);
        register(relations, "CONSUMES_FROM", RelationFamily.ASYNC, true);
        register(relations, "HAS_PARAM", RelationFamily.FLOW, true);
        register(relations, "CALLS_COMMAND", RelationFamily.BOUNDARY, true);
        register(relations, "READS_COLUMN", RelationFamily.DATABASE, true);
        register(relations, "WRITES_COLUMN", RelationFamily.DATABASE, true);
        register(relations, "HAS_VARIANT", RelationFamily.DATABASE, true);
        register(relations, "GUARDED_BY", RelationFamily.DATA, true);
        register(relations, "MAPS_TO_COLUMN", RelationFamily.DATABASE, true);
        register(relations, "MATCHES", RelationFamily.PLANNING, true);
        register(relations, "WATCHES", RelationFamily.WORKFLOW, true);
        register(relations, "REQUIRES_CHANGE", RelationFamily.PLANNING, true);
        register(relations, "SUGGESTS_REVIEW", RelationFamily.PLANNING, true);
        register(relations, "REQUIRES_TEST", RelationFamily.PLANNING, true);
        register(relations, "CHANGED_IN", RelationFamily.WORKFLOW, true);
        register(relations, "IMPACTS", RelationFamily.PLANNING, true);
        register(relations, "COVERED_BY", RelationFamily.PLANNING, true);
        register(relations, "HAS_DIAGNOSTIC", RelationFamily.WORKFLOW, true);
        register(relations, "CONFIRMED_SCOPE", RelationFamily.PLANNING, true);
        register(relations, "NOTIFIES", RelationFamily.WORKFLOW, true);
        register(relations, "ACKNOWLEDGES", RelationFamily.WORKFLOW, true);
        register(relations, "EXPORTS_SYMBOL", RelationFamily.BOUNDARY, true);
        register(relations, "REFERENCES_SYMBOL", RelationFamily.BOUNDARY, true);
    }

    private static void registerEnhancedRelations(Map<String, RelationType> relations) {
        register(relations, "CALLS_NATIVE", RelationFamily.BOUNDARY, false);
        register(relations, "HAS_NATIVE_BOUNDARY", RelationFamily.BOUNDARY, false);
        register(relations, "AUTO_BINDS_TO", RelationFamily.DATA, false);
        register(relations, "INTERCEPTS", RelationFamily.CALL, false);
        register(relations, "CONFIGURES_PROPERTY", RelationFamily.CONFIGURATION, false);
        register(relations, "SUMMARIZES", RelationFamily.PLANNING, false);
        register(relations, "COMMENTS_ON", RelationFamily.WORKFLOW, false);
        register(relations, "VIOLATES_POLICY", RelationFamily.WORKFLOW, false);
        register(relations, "SUPPRESSED_BY", RelationFamily.WORKFLOW, false);
        register(relations, "EXPORTED_AS", RelationFamily.WORKFLOW, false);
        register(relations, "REFLECTS_TO", RelationFamily.CANDIDATE, false);
        register(relations, "AI_SUGGESTS_RELATION", RelationFamily.AI_ASSISTED_CANDIDATE, false);
    }

    private static void register(
            Map<String, RelationType> relations,
            String name,
            RelationFamily family,
            boolean mvp) {
        RelationType previous = relations.putIfAbsent(name, new RelationType(name, family, mvp));
        if (previous != null) {
            throw new IllegalStateException("Duplicate relation kind: " + name);
        }
    }
}
