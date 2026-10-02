package com.vertx.worker.job;

import com.vertx.worker.monitor.EventLoopMonitor;
import com.vertx.worker.mvc.handler.RequestHandler;
import com.vertx.worker.mvc.handler.RouteHandler;
import com.vertx.worker.search.BookSearchIndex;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class BookJobHttpTest {
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final AtomicInteger deliveries = new AtomicInteger();
    private final CountDownLatch delivered = new CountDownLatch(1);
    private final AtomicReference<JsonObject> responseFailure = new AtomicReference<>();
    private final CountDownLatch responseFailed = new CountDownLatch(1);
    private final EventLoopMonitor monitor = new EventLoopMonitor() {
        @Override
        public void jobDispatchFailed(JsonObject trace, JsonObject job, Throwable cause) {
            responseFailure.set(job);
            responseFailed.countDown();
        }
    };
    private Vertx vertx;
    private BookJobRegistry registry;
    private String baseUrl;

    @AfterEach
    void closeResources() throws Exception {
        if (vertx != null) {
            await(vertx.close());
        }
    }

    @Test
    void parallelHttpRetriesReturnOneLocationAndDispatchOnce() throws Exception {
        start(8);
        List<CompletableFuture<HttpResponse<String>>> pending = IntStream.range(0, 12)
                .mapToObj(i -> client.sendAsync(post("/book/jobs/reindex", "same-key"), HttpResponse.BodyHandlers.ofString()))
                .toList();
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        var responses = pending.stream().map(CompletableFuture::join).toList();
        assertEquals(1, responses.stream().filter(response -> response.statusCode() == 202).count());
        assertEquals(11, responses.stream().filter(response -> response.statusCode() == 200).count());
        assertEquals(1, responses.stream().map(this::location).distinct().count());
        assertEquals(1, responses.stream().map(response -> data(response).getString("jobId")).distinct().count());
        for (HttpResponse<String> response : responses) {
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            if (response.statusCode() == 200) {
                assertEquals("true", response.headers().firstValue("Idempotency-Replayed").orElseThrow());
            }
        }
        assertTrue(delivered.await(5, TimeUnit.SECONDS));
        assertEquals(1, deliveries.get());
    }

    @Test
    void overloadRejectsNewWorkButAllowsReplayAndPolling() throws Exception {
        start(8);
        HttpResponse<String> first = send(post("/book/jobs/reindex", "first"));
        assertEquals(202, first.statusCode());
        assertTrue(delivered.await(5, TimeUnit.SECONDS));

        HttpResponse<String> rejected = send(post("/book/jobs/reindex", "second"));
        assertEquals(503, rejected.statusCode());
        assertEquals("1", rejected.headers().firstValue("Retry-After").orElseThrow());
        assertEquals("JOB_CAPACITY_EXCEEDED", data(rejected).getString("failureCode"));
        assertTrue(rejected.headers().firstValue("Location").isEmpty());
        assertEquals(200, send(post("/book/jobs/reindex", "first")).statusCode());
        assertEquals(200, send(HttpRequest.newBuilder(URI.create(baseUrl + location(first))).GET().build()).statusCode());
        assertEquals(1, deliveries.get());

        registry.require(data(first).getString("jobId")).markCompleted("done", new JsonObject());
        HttpResponse<String> retry = send(post("/book/jobs/reindex", "second"));
        assertEquals(202, retry.statusCode());
        assertNotEquals(location(first), location(retry));
    }

    @Test
    void historyCapacityPreservesCompletedReplay() throws Exception {
        start(1);
        HttpResponse<String> first = send(post("/book/jobs/reindex", "first"));
        assertTrue(delivered.await(5, TimeUnit.SECONDS));
        registry.require(data(first).getString("jobId")).markCompleted("done", new JsonObject());

        HttpResponse<String> rejected = send(post("/book/jobs/reindex", "second"));
        assertEquals(503, rejected.statusCode());
        assertEquals("JOB_HISTORY_FULL", data(rejected).getString("failureCode"));
        HttpResponse<String> replay = send(post("/book/jobs/reindex", "first"));
        assertEquals(200, replay.statusCode());
        assertEquals(location(first), location(replay));
        assertEquals("COMPLETED", data(replay).getString("status"));
        assertEquals(1, deliveries.get());
    }

    @Test
    void conflictsAndInvalidKeysCannotDispatch() throws Exception {
        start(8);
        HttpResponse<String> first = send(post("/book/jobs/reindex", "key"));
        assertEquals(202, first.statusCode());
        assertTrue(delivered.await(5, TimeUnit.SECONDS));

        HttpResponse<String> conflict = send(post("/book/jobs/reindex?fail=dispatch", "key"));
        assertEquals(409, conflict.statusCode());
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", data(conflict).getString("failureCode"));
        assertTrue(conflict.headers().firstValue("Location").isEmpty());
        assertEquals(400, send(post("/book/jobs/reindex", "bad key")).statusCode());
        assertEquals(400, send(post("/book/jobs/reindex", "x".repeat(129))).statusCode());
        HttpRequest duplicateHeaders = HttpRequest.newBuilder(URI.create(baseUrl + "/book/jobs/reindex"))
                .header("Idempotency-Key", "a").header("Idempotency-Key", "b")
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(400, send(duplicateHeaders).statusCode());
        assertEquals(1, deliveries.get());
    }

    @Test
    void missingConsumerFailureIsReplayableAndReleasesCapacity() throws Exception {
        start(8);
        HttpResponse<String> first = send(post("/book/jobs/reindex?fail=dispatch", "missing"));
        assertEquals(202, first.statusCode());
        assertTrue(responseFailed.await(5, TimeUnit.SECONDS));
        HttpResponse<String> replay = send(post("/book/jobs/reindex?fail=dispatch", "missing"));
        assertEquals(200, replay.statusCode());
        assertEquals(location(first), location(replay));
        assertEquals("DISPATCH_FAILED", data(replay).getString("status"));
        assertEquals(0, deliveries.get());
        assertEquals(202, send(post("/book/jobs/reindex", "next")).statusCode());
    }

    @Test
    void failedResponseWriteTerminatesJobWithoutDispatch() throws Exception {
        start(8);
        assertThrows(IOException.class, () -> send(post("/disconnect", "lost-response")));
        assertTrue(responseFailed.await(5, TimeUnit.SECONDS));
        assertEquals("DISPATCH_FAILED", responseFailure.get().getString("status"));
        assertEquals("HTTP_RESPONSE_FAILED", responseFailure.get().getString("failureCode"));
        assertEquals(0, deliveries.get());
        HttpResponse<String> replay = send(post("/book/jobs/reindex", "lost-response"));
        assertEquals(200, replay.statusCode());
        assertEquals("HTTP_RESPONSE_FAILED", data(replay).getString("failureCode"));
        assertEquals(202, send(post("/book/jobs/reindex", "next")).statusCode());
    }

    private void start(int retained) throws Exception {
        vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(2));
        registry = new BookJobRegistry(1, retained, 900);
        MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer(BookReindexJobWorker.ADDRESS, message -> {
            deliveries.incrementAndGet();
            BookJob job = registry.require(message.body().getString("jobId"));
            job.markRunning(1);
            message.reply(new JsonObject().put("received", true));
            delivered.countDown();
            // Keep the job running until the test explicitly completes it.
        });
        await(consumer.completion());
        Router root = Router.router(vertx);
        root.route("/book/*").subRouter(new RouteHandler(vertx, null, monitor, registry, new BookSearchIndex(), null).getRouter());
        RequestHandler handler = new RequestHandler(null, monitor, registry, new BookSearchIndex(), null, vertx);
        root.post("/disconnect").handler(context ->
                context.request().connection().close().onComplete(ignored -> handler.createReindexJob(context)));
        var server = await(vertx.createHttpServer().requestHandler(root).listen(0, "127.0.0.1"));
        baseUrl = "http://127.0.0.1:" + server.actualPort();
    }

    private HttpRequest post(String path, String key) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(5))
                .header("Idempotency-Key", key).POST(HttpRequest.BodyPublishers.noBody()).build();
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElseThrow();
    }

    private JsonObject data(HttpResponse<String> response) {
        return new JsonObject(response.body()).getJsonObject("data");
    }

    private <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
