package org.sainm.codeatlas.graph.model;

public enum RelationType {
    CONTAINS,
    DECLARES,
    CALLS,
    IMPLEMENTS,
    EXTENDS,
    INJECTS,
    ROUTES_TO,
    SUBMITS_TO,
    INCLUDES,
    BINDS_TO,
    PASSES_PARAM,
    READS_PARAM,
    WRITES_PARAM,
    READS_TABLE,
    WRITES_TABLE,
    USES_CONFIG,
    FORWARDS_TO,
    CHANGED_IN,
    IMPACTS,
    COVERED_BY,
    BRIDGES_TO,
    SYNTHETIC_OF
}
