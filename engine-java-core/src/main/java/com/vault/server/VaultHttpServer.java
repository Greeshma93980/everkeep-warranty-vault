package com.vault.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vault.pdf.DossierGenerator;
import com.vault.rules.WarrantyCalculator;
import com.vault.scheduler.ExpiryScheduler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VaultHttpServer {
    private static final Gson GSON = new Gson();

    private VaultHttpServer() {}

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        var scheduler = new ExpiryScheduler();
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/health", exchange ->
                sendJson(exchange, 200, Map.of("status", "ok", "service", "engine-java-core")));
        server.createContext("/api/evaluate", VaultHttpServer::evaluate);
        server.createContext("/api/dossier", VaultHttpServer::dossier);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            scheduler.close();
        }));
        server.start();
        System.out.println("Vault Java engine listening on " + port);
    }

    private static void evaluate(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "POST required"));
            return;
        }

        try {
            JsonObject body = JsonParser.parseString(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            LocalDate purchase = LocalDate.parse(body.get("purchase_date").getAsString());
            LocalDate today = LocalDate.now();
            JsonArray results = new JsonArray();

            for (JsonElement element : body.getAsJsonArray("components")) {
                JsonObject component = element.getAsJsonObject();
                var result = WarrantyCalculator.calculate(
                        str(component, "name", "Component"),
                        str(component, "type", "STANDARD"),
                        purchase,
                        integer(component, "duration_months", 0),
                        decimal(component, "replacement_value", 0),
                        today);
                JsonObject row = new JsonObject();
                row.addProperty("name", result.name());
                row.addProperty("type", result.type());
                row.addProperty("expiry_date", result.expiryDate().toString());
                row.addProperty("remaining_days", result.remainingDays());
                row.addProperty("status", result.status());
                row.addProperty("reminder_tier", result.reminderTier().name());
                row.addProperty("replacement_discount", result.replacementDiscount());
                results.add(row);
            }

            sendJson(exchange, 200, Map.of("components", results));
        } catch (Exception ex) {
            sendJson(exchange, 400, Map.of(
                    "error", "Invalid evaluation request",
                    "detail", String.valueOf(ex.getMessage())));
        }
    }

    private static void dossier(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "POST required"));
            return;
        }

        try {
            JsonObject body = JsonParser.parseString(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            byte[] pdf = DossierGenerator.generate(
                    str(body, "asset_name", "Asset"),
                    str(body, "serial_number", ""),
                    str(body, "sha256_hash", ""),
                    dossierCoverages(body));
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.getResponseHeaders().set(
                    "Content-Disposition", "attachment; filename=asset-dossier.pdf");
            exchange.sendResponseHeaders(200, pdf.length);
            exchange.getResponseBody().write(pdf);
            exchange.close();
        } catch (Exception ex) {
            sendJson(exchange, 400, Map.of(
                    "error", "Unable to generate dossier",
                    "detail", String.valueOf(ex.getMessage())));
        }
    }

    private static String str(JsonObject object, String key, String defaultValue) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString()
                : defaultValue;
    }

    private static List<WarrantyCalculator.Result> dossierCoverages(JsonObject body) {
        List<WarrantyCalculator.Result> coverages = new ArrayList<>();
        if (!body.has("components") || !body.get("components").isJsonArray()) {
            return coverages;
        }

        for (JsonElement element : body.getAsJsonArray("components")) {
            JsonObject component = element.getAsJsonObject();
            coverages.add(new WarrantyCalculator.Result(
                    str(component, "name", "Component"),
                    str(component, "type", "STANDARD"),
                    LocalDate.parse(str(component, "expiry_date", LocalDate.now().toString())),
                    integer(component, "remaining_days", 0),
                    str(component, "status", "Unknown"),
                    0,
                    WarrantyCalculator.classifyReminderTier(
                            integer(component, "remaining_days", 0))));
        }
        return coverages;
    }

    private static int integer(JsonObject object, String key, int defaultValue) {
        return object.has(key) ? object.get(key).getAsInt() : defaultValue;
    }

    private static double decimal(JsonObject object, String key, double defaultValue) {
        return object.has(key) ? object.get(key).getAsDouble() : defaultValue;
    }

    private static void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
