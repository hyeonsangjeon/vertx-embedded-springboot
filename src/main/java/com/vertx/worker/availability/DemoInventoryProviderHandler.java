package com.vertx.worker.availability;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/** Self-contained downstream HTTP services used by the availability fan-out demo. */
public class DemoInventoryProviderHandler {
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";

    private final Vertx vertx;

    public DemoInventoryProviderHandler(Vertx vertx) {
        this.vertx = vertx;
    }

    public void getAvailability(RoutingContext routingContext) {
        DemoInventoryProvider provider = DemoInventoryProvider.fromSlug(routingContext.pathParam("provider"))
                .orElse(null);
        Long bookId = positiveBookId(routingContext.pathParam("bookId"));

        if (provider == null || bookId == null) {
            int statusCode = provider == null ? 404 : 400;
            String message = provider == null ? "inventory provider not found" : "bookId must be a positive number";
            end(routingContext, statusCode, new JsonObject().put("message", message));
            return;
        }

        vertx.setTimer(provider.latencyMs(), ignored -> {
            if ("true".equalsIgnoreCase(routingContext.request().getParam("fail"))) {
                end(routingContext, 503, new JsonObject()
                        .put("provider", provider.slug())
                        .put("message", "simulated downstream failure"));
                return;
            }

            end(routingContext, 200, new JsonObject()
                    .put("provider", provider.slug())
                    .put("bookId", bookId)
                    .put("status", provider.quantity() > 0 ? "AVAILABLE" : "OUT_OF_STOCK")
                    .put("quantity", provider.quantity())
                    .put("simulatedLatencyMs", provider.latencyMs())
                    .put("servedByThread", Thread.currentThread().getName()));
        });
    }

    private Long positiveBookId(String value) {
        try {
            long bookId = Long.parseLong(value);
            return bookId > 0 ? bookId : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void end(RoutingContext routingContext, int statusCode, JsonObject body) {
        routingContext.response()
                .setStatusCode(statusCode)
                .putHeader("content-type", CONTENT_TYPE)
                .end(body.encode());
    }
}
