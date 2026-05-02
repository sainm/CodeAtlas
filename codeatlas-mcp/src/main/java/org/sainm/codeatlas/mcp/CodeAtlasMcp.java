package org.sainm.codeatlas.mcp;

import org.sainm.codeatlas.ai.CodeAtlasAi;
import org.sainm.codeatlas.server.CodeAtlasServer;

public final class CodeAtlasMcp {
    private CodeAtlasMcp() {
    }

    public static String moduleName() {
        return "codeatlas-mcp";
    }

    public static String dependencySummary() {
        return CodeAtlasServer.health().service() + "," + CodeAtlasAi.moduleName();
    }
}
