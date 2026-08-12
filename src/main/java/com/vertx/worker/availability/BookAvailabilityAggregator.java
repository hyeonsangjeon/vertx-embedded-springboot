package com.vertx.worker.availability;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

import java.util.List;

import static io.vertx.core.http.HttpResponseExpectation.JSON;
import static io.vertx.core.http.HttpResponseExpectation.SC_SUCCESS;

/**
 * Calls independent inventory services concurrently on the event loop and combines their responses.
 * Blocking work does not belong here; each call is asynchronous HTTP I/O.
 */
public class BookAvailabilityAggregator implements AutoCloseable {
    private static final String HOST = "127.0.0.1";
    private static final long REQUEST_TIMEOUT_MS = 1_000;

    private final WebClient client;
    private final int port;

    public BookAvailabilityAggregator(Vertx vertx, int port) {
        this(WebClient.create(vertx), port);
    }

    BookAvailabilityAggregator(WebClient client, int port) {
        this.client = client;
        this.port = port;
    }

    public Future<JsonObject> check(long bookId, String failedProvider) {
        long startedAt = System.nanoTime();
        List<Future<JsonObject>> calls = DemoInventoryProvider.all().stream()
                .map(provider -> fetch(provider, bookId, provider.slug().equals(failedProvider)))
                .toList();

        return Future.all(calls).map(ignored -> aggregate(bookId, calls, startedAt));
    }

    public boolean supportsProvider(String provider) {
        return provider == null || DemoInventoryProvider.fromSlug(provider).isPresent();
    }

    public JsonArray providerNames() {
        return new JsonArray(DemoInventoryProvider.slugs());
    }

    private Future<JsonObject> fetch(DemoInventoryProvider provider, long bookId, boolean fail) {
        long startedAt = System.nanoTime();
        String path = "/demo/inventory/" + provider.slug() + "/books/" + bookId;

        var request = client.get(port, HOST, path)
                .timeout(REQUEST_TIMEOUT_MS)
                .as(BodyCodec.jsonObject());
        if (fail) {
            request.addQueryParam("fail", "true");
        }

        return request.send()
                .expecting(SC_SUCCESS.and(JSON))
                .map(response -> response.body().copy()
                        .put("elapsedMs", elapsedMillis(startedAt)))
                .recover(cause -> Future.succeededFuture(new JsonObject()
                        .put("provider", provider.slug())
                        .put("bookId", bookId)
                        .put("status", "UNAVAILABLE")
                        .put("quantity", 0)
                        .put("elapsedMs", elapsedMillis(startedAt))
                        .put("error", failureMessage(cause))));
    }

    private JsonObject aggregate(long bookId, List<Future<JsonObject>> calls, long startedAt) {
        JsonArray offers = new JsonArray();
        calls.stream().map(Future::result).forEach(offers::add);

        int unavailable = 0;
        int totalQuantity = 0;
        for (Object value : offers) {
            JsonObject offer = (JsonObject) value;
            if ("UNAVAILABLE".equals(offer.getString("status"))) {
                unavailable++;
            }
            totalQuantity += offer.getInteger("quantity", 0);
        }

        return new JsonObject()
                .put("bookId", bookId)
                .put("strategy", "concurrent-http-fan-out")
                .put("providerCount", offers.size())
                .put("respondedProviders", offers.size() - unavailable)
                .put("unavailableProviders", unavailable)
                .put("totalQuantity", totalQuantity)
                .put("partial", unavailable > 0)
                .put("simulatedSequentialLatencyMs", DemoInventoryProvider.all().stream()
                        .mapToLong(DemoInventoryProvider::latencyMs)
                        .sum())
                .put("elapsedMs", elapsedMillis(startedAt))
                .put("offers", offers);
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String failureMessage(Throwable cause) {
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    @Override
    public void close() {
        client.close();
    }
}
