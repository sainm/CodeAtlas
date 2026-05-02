package org.sainm.codeatlas.worker;

import org.sainm.codeatlas.analyzers.CodeAtlasAnalyzers;
import org.sainm.codeatlas.graph.CodeAtlasGraph;

public final class CodeAtlasWorker {
    private CodeAtlasWorker() {
    }

    public static String moduleName() {
        return "codeatlas-worker";
    }

    public static String dependencySummary() {
        return CodeAtlasAnalyzers.moduleName() + "," + CodeAtlasGraph.moduleName();
    }
}
