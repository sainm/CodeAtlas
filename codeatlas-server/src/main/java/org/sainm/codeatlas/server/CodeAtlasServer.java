package org.sainm.codeatlas.server;

import java.net.InetSocketAddress;

public final class CodeAtlasServer {
    private CodeAtlasServer() {
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        CodeAtlasHttpServer server = new CodeAtlasHttpServer(new InetSocketAddress(port), new InMemoryReportStore());
        server.start();
        System.out.println("CodeAtlas server listening on http://localhost:" + server.port());
    }
}
