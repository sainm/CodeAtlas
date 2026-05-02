package org.sainm.codeatlas.analyzers;

import org.sainm.codeatlas.facts.CodeAtlasFacts;

public final class CodeAtlasAnalyzers {
    private CodeAtlasAnalyzers() {
    }

    public static String moduleName() {
        return "codeatlas-analyzers";
    }

    public static String dependsOn() {
        return CodeAtlasFacts.moduleName();
    }
}
