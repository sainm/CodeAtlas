package org.sainm.codeatlas.facts;

import org.sainm.codeatlas.symbols.CodeAtlasSymbols;

public final class CodeAtlasFacts {
    private CodeAtlasFacts() {
    }

    public static String moduleName() {
        return "codeatlas-facts";
    }

    public static String dependsOn() {
        return CodeAtlasSymbols.moduleName();
    }
}
