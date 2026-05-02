package org.sainm.codeatlas.graph;

import org.sainm.codeatlas.facts.CodeAtlasFacts;

public final class CodeAtlasGraph {
    private CodeAtlasGraph() {
    }

    public static String moduleName() {
        return "codeatlas-graph";
    }

    public static String dependsOn() {
        return CodeAtlasFacts.moduleName();
    }
}
