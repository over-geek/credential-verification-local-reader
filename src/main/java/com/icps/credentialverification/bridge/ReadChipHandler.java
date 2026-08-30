package com.icps.credentialverification.bridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ReadChipHandler implements HttpHandler {

    private final ChipReader chipReader;

    public ReadChipHandler(ChipReader chipReader) {
        this.chipReader = chipReader;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"Only GET is supported.\"}");
            return;
        }

        try {
            String chipUid = chipReader.readChipUid();
            send(exchange, 200, "{\"chip_uid\":\"" + escapeJson(chipUid) + "\"}");
        } catch (ChipReadException exception) {
            send(exchange, exception.getStatusCode(), "{\"error\":\"" + escapeJson(exception.getMessage()) + "\"}");
        } catch (RuntimeException exception) {
            send(exchange, 500, "{\"error\":\"Unexpected bridge agent error.\"}");
        }
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    }

    private void send(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, response.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
