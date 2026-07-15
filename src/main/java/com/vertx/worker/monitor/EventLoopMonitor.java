package com.vertx.worker.monitor;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class EventLoopMonitor {
    private static final String EVENT_ADDRESS = EventLoopMonitor.class.getName() + ".events";
    private static final String SSE_CONTENT_TYPE = "text/event-stream; charset=utf-8";

    private final CopyOnWriteArrayList<HttpServerResponse> clients = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    private volatile Vertx vertx;
    private MessageConsumer<JsonObject> consumer;

    public Future<Void> bind(Vertx vertx) {
        if (consumer != null) {
            return Future.succeededFuture();
        }

        this.vertx = vertx;
        consumer = vertx.eventBus().consumer(EVENT_ADDRESS, message -> broadcast(message.body()));
        return consumer.completion();
    }

    public void connect(RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response()
                .setStatusCode(200)
                .putHeader("content-type", SSE_CONTENT_TYPE)
                .putHeader("cache-control", "no-cache")
                .putHeader("connection", "keep-alive")
                .putHeader("Access-Control-Allow-Origin", "*")
                .setChunked(true);

        clients.add(response);
        response.closeHandler(ignored -> clients.remove(response));
        write(response, event("monitor.connected", "sse", null, null)
                .put("clientCount", clients.size()));
    }

    public JsonObject startHttpRequest(RoutingContext routingContext, String operation) {
        JsonObject trace = new JsonObject()
                .put("requestId", UUID.randomUUID().toString())
                .put("operation", operation)
                .put("method", routingContext.request().method().name())
                .put("path", routingContext.normalizedPath())
                .put("startedAtNanos", System.nanoTime());

        publish("event-loop.received", operation, trace, null);
        return trace;
    }

    public void dispatchToWorker(JsonObject trace, String step) {
        publish("event-loop.dispatch", step, trace, null);
    }

    public void workerStarted(JsonObject trace, String step) {
        publish("worker.started", step, trace, null);
    }

    public void workerCompleted(JsonObject trace, String step) {
        publish("worker.completed", step, trace, null);
    }

    public void workerFailed(JsonObject trace, String step, Throwable cause) {
        publish("worker.failed", step, trace, error(cause));
    }

    public void httpCompleted(JsonObject trace, int statusCode) {
        publish("event-loop.completed", trace.getString("operation"), trace,
                new JsonObject().put("statusCode", statusCode));
    }

    public void httpFailed(JsonObject trace, Throwable cause) {
        publish("event-loop.failed", trace.getString("operation"), trace, error(cause));
    }

    public void jobAccepted(JsonObject trace, JsonObject job) {
        publish("job.accepted", job.getString("type"), trace, job);
    }

    public void jobStarted(JsonObject trace, JsonObject job) {
        publish("job.started", job.getString("type"), trace, job);
    }

    public void jobProgress(JsonObject trace, JsonObject job) {
        publish("job.progress", job.getString("type"), trace, job);
    }

    public void jobCompleted(JsonObject trace, JsonObject job) {
        publish("job.completed", job.getString("type"), trace, job);
    }

    public void jobFailed(JsonObject trace, JsonObject job, Throwable cause) {
        publish("job.failed", job.getString("type"), trace, job.copy().put("failure", error(cause)));
    }

    private void publish(String phase, String step, JsonObject trace, JsonObject detail) {
        Vertx current = vertx;
        if (current == null) {
            return;
        }
        current.eventBus().publish(EVENT_ADDRESS, event(phase, step, trace, detail));
    }

    private JsonObject event(String phase, String step, JsonObject trace, JsonObject detail) {
        JsonObject event = new JsonObject()
                .put("sequence", sequence.incrementAndGet())
                .put("timestamp", Instant.now().toString())
                .put("phase", phase)
                .put("step", step)
                .put("thread", Thread.currentThread().getName());

        if (trace != null) {
            event.put("requestId", trace.getString("requestId"))
                    .put("operation", trace.getString("operation"))
                    .put("method", trace.getString("method"))
                    .put("path", trace.getString("path"))
                    .put("elapsedMs", elapsedMillis(trace));
        }
        if (detail != null) {
            event.put("detail", detail);
        }
        return event;
    }

    private long elapsedMillis(JsonObject trace) {
        Long startedAtNanos = trace.getLong("startedAtNanos");
        if (startedAtNanos == null) {
            return 0;
        }
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private JsonObject error(Throwable cause) {
        return new JsonObject()
                .put("error", cause.getClass().getSimpleName())
                .put("message", cause.getMessage() == null ? "unexpected failure" : cause.getMessage());
    }

    private void broadcast(JsonObject event) {
        clients.forEach(client -> write(client, event));
    }

    private void write(HttpServerResponse response, JsonObject event) {
        try {
            String frame = "id: " + event.getLong("sequence") + "\n"
                    + "event: " + event.getString("phase") + "\n"
                    + "data: " + event.encode() + "\n\n";
            response.write(frame).onFailure(ignored -> clients.remove(response));
        } catch (IllegalStateException e) {
            clients.remove(response);
        }
    }
}
