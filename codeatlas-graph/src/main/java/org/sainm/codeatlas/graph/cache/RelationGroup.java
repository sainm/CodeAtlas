package org.sainm.codeatlas.graph.cache;

import org.sainm.codeatlas.graph.model.RelationType;
import java.util.EnumSet;
import java.util.Set;

public enum RelationGroup {
    CALL(EnumSet.of(RelationType.CALLS)),
    IMPACT(EnumSet.of(
        RelationType.CALLS,
        RelationType.ROUTES_TO,
        RelationType.SUBMITS_TO,
        RelationType.BINDS_TO,
        RelationType.FORWARDS_TO,
        RelationType.READS_TABLE,
        RelationType.WRITES_TABLE,
        RelationType.READS_PARAM,
        RelationType.WRITES_PARAM
    ));

    private final Set<RelationType> relationTypes;

    RelationGroup(Set<RelationType> relationTypes) {
        this.relationTypes = Set.copyOf(relationTypes);
    }

    public Set<RelationType> relationTypes() {
        return relationTypes;
    }
}
