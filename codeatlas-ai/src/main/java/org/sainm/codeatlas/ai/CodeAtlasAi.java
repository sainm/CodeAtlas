package org.sainm.codeatlas.ai;

import org.sainm.codeatlas.facts.CodeAtlasFacts;

public final class CodeAtlasAi {
    private CodeAtlasAi() {
    }

    public static String moduleName() {
        return "codeatlas-ai";
    }

    public static String dependsOn() {
        return CodeAtlasFacts.moduleName();
    }
}
