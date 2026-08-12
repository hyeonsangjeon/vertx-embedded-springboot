package com.vertx.worker.availability;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookAvailabilityAggregatorTest {
    private Vertx vertx;
    private HttpServer server;
    private BookAvailabilityAggregator aggregator;

    @AfterEach
    void closeResources() throws Exception {
        if (aggregator != null) {
            aggregator.close();
        }
        if (server != null) {
            await(server.close());
        }
        if (vertx != null) {
            await(vertx.close());
        }
    }

    @Test
    void startsEveryProviderRequestBeforeWaitingForAResponse() throws Exception {
        vertx = Vertx.vertx();
        List<PendingResponse> pending = new ArrayList<>();

        server = await(vertx.createHttpServer()
                .requestHandler(request -> {
                    List<PendingResponse> ready = null;
                    synchronized (pending) {
                        pending.add(new PendingResponse(providerFrom(request.path()), request.response()));
                        if (pending.size() == DemoInventoryProvider.all().size()) {
                            ready = List.copyOf(pending);
                        }
                    }
                    if (ready != null) {
                        ready.forEach(this::completeAvailableResponse);
                    }
                })
                .listen(0));

        aggregator = new BookAvailabilityAggregator(vertx, server.actualPort());
        JsonObject result = await(aggregator.check(7, null));

        assertEquals(3, result.getInteger("providerCount"));
        assertEquals(0, result.getInteger("unavailableProviders"));
        assertEquals(6, result.getInteger("totalQuantity"));
        assertEquals(740L, result.getLong("simulatedSequentialLatencyMs"));
    }

    @Test
    void preservesSuccessfulResultsWhenOneProviderFails() throws Exception {
        vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        DemoInventoryProviderHandler providerHandler = new DemoInventoryProviderHandler(vertx);
        router.get("/demo/inventory/:provider/books/:bookId")
                .handler(providerHandler::getAvailability);
        server = await(vertx.createHttpServer().requestHandler(router).listen(0));

        aggregator = new BookAvailabilityAggregator(vertx, server.actualPort());
        JsonObject result = await(aggregator.check(11, "busan"));

        assertTrue(result.getBoolean("partial"));
        assertEquals(1, result.getInteger("unavailableProviders"));
        assertEquals(8, result.getInteger("totalQuantity"));

        JsonArray offers = result.getJsonArray("offers");
        JsonObject busan = offers.stream()
                .map(JsonObject.class::cast)
                .filter(offer -> "busan".equals(offer.getString("provider")))
                .findFirst()
                .orElseThrow();
        assertEquals("UNAVAILABLE", busan.getString("status"));
    }

    private void completeAvailableResponse(PendingResponse pending) {
        pending.response()
                .putHeader("content-type", "application/json")
                .end(new JsonObject()
                        .put("provider", pending.provider())
                        .put("bookId", 7)
                        .put("status", "AVAILABLE")
                        .put("quantity", 2)
                        .put("simulatedLatencyMs", 0)
                        .encode());
    }

    private String providerFrom(String path) {
        return path.split("/")[3];
    }

    private <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private record PendingResponse(String provider, HttpServerResponse response) {
    }
}
