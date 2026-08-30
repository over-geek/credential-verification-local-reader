package com.icps.credentialverification.bridge;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class BridgeAgentApplication {

    private static final int DEFAULT_PORT = 9000;

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        ChipReader chipReader = new PcscChipReader();
        server.createContext("/read-chip", new ReadChipHandler(chipReader));
        server.createContext("/write-chip", new WriteChipHandler(chipReader));
        server.createContext("/reset-chip", new ResetChipHandler(chipReader));
        server.setExecutor(null);
        server.start();

        System.out.println("Local bridge agent listening on http://localhost:" + port);
        System.out.println("Endpoints: /read-chip, /write-chip, /reset-chip");
        System.out.println("Press Ctrl+C to stop.");
    }

    private static int resolvePort(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                return Integer.parseInt(arg.substring("--port=".length()));
            }
        }

        String envPort = System.getenv("BRIDGE_AGENT_PORT");
        if (envPort != null && !envPort.isBlank()) {
            return Integer.parseInt(envPort);
        }

        return DEFAULT_PORT;
    }
}
