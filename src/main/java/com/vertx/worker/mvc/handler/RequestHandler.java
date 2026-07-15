package com.vertx.worker.mvc.handler;

import com.vertx.worker.job.BookJob;
import com.vertx.worker.job.BookJobRegistry;
import com.vertx.worker.job.BookReindexJobWorker;
import com.vertx.worker.monitor.EventLoopMonitor;
import com.vertx.worker.mvc.service.BookAsyncService;
import com.vertx.worker.search.BookSearchIndex;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.springframework.http.HttpStatus;

/**
 * HTTP boundary that keeps event-loop work small and dispatches blocking operations to worker verticles.
 */
public class RequestHandler {

    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private final BookAsyncService bookAsyncService;
    private final EventLoopMonitor monitor;
    private final BookJobRegistry jobRegistry;
    private final BookSearchIndex searchIndex;
    private final Vertx vertx;

    public RequestHandler(
            BookAsyncService bookAsyncService,
            EventLoopMonitor monitor,
            BookJobRegistry jobRegistry,
            BookSearchIndex searchIndex,
            Vertx vertx) {
        this.bookAsyncService = bookAsyncService;
        this.monitor = monitor;
        this.jobRegistry = jobRegistry;
        this.searchIndex = searchIndex;
        this.vertx = vertx;
    }

    public void createBook(RoutingContext routingContext) {
        JsonObject trace = monitor.startHttpRequest(routingContext, "book.create");
        JsonObject request = requestBody(routingContext, trace);
        if (request == null || !validateBook(routingContext, trace, request)) {
            return;
        }

        dispatch(trace, "book.save");
        sendFutureResult(routingContext, trace, bookAsyncService.save(request, trace));
    }

    public void getAll(RoutingContext routingContext) {
        JsonObject trace = monitor.startHttpRequest(routingContext, "book.list");
        dispatch(trace, "book.list");
        sendFutureResult(routingContext, trace, bookAsyncService.getAll(trace));
    }

    public void get(RoutingContext routingContext) {
        JsonObject trace = monitor.startHttpRequest(routingContext, "book.get");
        Long bookId = bookIdParam(routingContext, trace);
        if (bookId == null) {
            return;
        }

        dispatch(trace, "book.get");
        sendFutureResult(routingContext, trace, bookAsyncService.get(bookId, trace));
    }

    public void updateBook(RoutingContext routingContext) {
        JsonObject trace = monitor.startHttpRequest(routingContext, "book.update");
        JsonObject request = requestBody(routingContext, trace);
        if (request == null || !validateBook(routingContext, trace, request)) {
            return;
        }

        Long bookId = longField(request, "id");
        if (bookId == null) {
            sendBadRequest(routingContext, trace, "numeric book id is required");
            return;
        }
        request.put("id", bookId);

        dispatch(trace, "book.get");
        Future<JsonObject> update = bookAsyncService.get(bookId, trace).compose(found -> {
            if (!hasDataId(found)) {
                return Future.succeededFuture(found);
            }
            dispatch(trace, "book.update");
            return bookAsyncService.update(request, trace);
        });
        sendFutureResult(routingContext, trace, update);
    }

    public void deleteBook(RoutingContext routingContext) {
        JsonObject trace = monitor.startHttpRequest(routingContext, "book.delete");
        Long bookId = bookIdParam(routingContext, trace);
        if (bookId == null) {
            return;
        }

        dispatch(trace, "book.get");
        Future<JsonObject> deletion = bookAsyncService.get(bookId, trace).compose(found -> {
            if (!hasDataId(found)) {
                return Future.succeededFuture(found);
            }
            dispatch(trace, "book.delete");
            return bookAsyncService.delete(bookId, trace)
                    .map(result -> result.copy().put("data", found.getJsonObject("data")));
        });
        sendFutureResult(routingContext, trace, deletion);
    }

    public void events(RoutingContext routingContext) {
        monitor.connect(routingContext);
    }

    public void createReindexJob(RoutingContext routingContext) {
        JsonObject trace = monitor.startHttpRequest(routingContext, "job.search-index.rebuild");
        BookJob job = jobRegistry.accept("book.search-index.rebuild");
        JsonObject responseJob = withLinks(job.toJson());
        String statusPath = "/book/jobs/" + job.getId();

        monitor.jobAccepted(trace, job.toJson());
        routingContext.response()
                .putHeader("Location", statusPath)
                .putHeader("Retry-After", "1");

        JsonObject result = new JsonObject()
                .put("statusCode", HttpStatus.ACCEPTED.value())
                .put("data", responseJob)
                .put("message", "search index rebuild accepted");

        sendResult(routingContext.response(), trace, result)
                .onComplete(ignored -> dispatchReindexJob(trace, job));
    }

    public void getJob(RoutingContext routingContext) {
        JsonObject trace = monitor.startHttpRequest(routingContext, "job.get");
        String jobId = routingContext.pathParam("jobId");

        JsonObject result = jobRegistry.find(jobId)
                .map(job -> new JsonObject()
                        .put("statusCode", HttpStatus.OK.value())
                        .put("data", withLinks(job.toJson()))
                        .put("message", "job status"))
                .orElseGet(() -> new JsonObject()
                        .put("statusCode", HttpStatus.NOT_FOUND.value())
                        .put("data", new JsonObject())
                        .put("message", "job not found"));

        sendResult(routingContext.response(), trace, result);
    }

    public void searchBooks(RoutingContext routingContext) {
        JsonObject trace = monitor.startHttpRequest(routingContext, "book.search");
        String query = routingContext.request().getParam("q");
        if (query == null || query.isBlank()) {
            sendBadRequest(routingContext, trace, "query parameter q is required");
            return;
        }

        JsonArray matches = searchIndex.search(query);
        JsonObject result = new JsonObject()
                .put("statusCode", HttpStatus.OK.value())
                .put("data", new JsonObject()
                        .put("query", query)
                        .put("indexedDocuments", searchIndex.size())
                        .put("matchCount", matches.size())
                        .put("results", matches))
                .put("message", "search completed on the in-memory index");
        sendResult(routingContext.response(), trace, result);
    }

    public void failure(RoutingContext routingContext) {
        if (routingContext.response().ended()) {
            return;
        }
        JsonObject result = new JsonObject()
                .put("statusCode", HttpStatus.INTERNAL_SERVER_ERROR.value())
                .put("data", new JsonObject())
                .put("message", "internal server error");
        sendResult(routingContext.response(), null, result);
    }

    private void dispatchReindexJob(JsonObject trace, BookJob job) {
        dispatch(trace, "job.search-index.rebuild");
        JsonObject command = new JsonObject()
                .put("jobId", job.getId())
                .put("trace", trace);

        vertx.eventBus().send(BookReindexJobWorker.ADDRESS, command);
    }

    private JsonObject withLinks(JsonObject job) {
        String jobId = job.getString("jobId");
        return job.copy().put("links", new JsonObject()
                .put("status", "/book/jobs/" + jobId)
                .put("events", "/book/events")
                .put("search", "/book/search?q=Hyeon-Sang"));
    }

    private JsonObject requestBody(RoutingContext routingContext, JsonObject trace) {
        try {
            JsonObject body = routingContext.body().asJsonObject();
            if (body == null) {
                sendBadRequest(routingContext, trace, "JSON body is required");
            }
            return body;
        } catch (RuntimeException e) {
            sendBadRequest(routingContext, trace, "invalid JSON body");
            return null;
        }
    }

    private boolean validateBook(RoutingContext routingContext, JsonObject trace, JsonObject book) {
        Object name = book.getValue("name");
        if (!(name instanceof String title) || title.isBlank()) {
            sendBadRequest(routingContext, trace, "book name is required");
            return false;
        }

        Object pages = book.getValue("pages");
        if (pages != null && (!(pages instanceof Number) || ((Number) pages).intValue() < 0)) {
            sendBadRequest(routingContext, trace, "book pages must be a non-negative number");
            return false;
        }
        return true;
    }

    private Long bookIdParam(RoutingContext routingContext, JsonObject trace) {
        String bookId = routingContext.pathParam("bookId");
        try {
            return bookId == null ? null : Long.parseLong(bookId);
        } catch (NumberFormatException e) {
            sendBadRequest(routingContext, trace, "bookId must be a number");
            return null;
        }
    }

    private Long longField(JsonObject json, String field) {
        Object value = json.getValue(field);
        return value instanceof Number number ? number.longValue() : null;
    }

    private void dispatch(JsonObject trace, String step) {
        monitor.dispatchToWorker(trace, step);
    }

    private void sendFutureResult(RoutingContext routingContext, JsonObject trace, Future<JsonObject> future) {
        future.onSuccess(result -> sendResult(routingContext.response(), trace, result))
                .onFailure(cause -> {
                    monitor.httpFailed(trace, cause);
                    routingContext.fail(cause);
                });
    }

    private boolean hasDataId(JsonObject result) {
        JsonObject data = result.getJsonObject("data");
        return data != null && data.getValue("id") != null;
    }

    private void sendBadRequest(RoutingContext routingContext, JsonObject trace, String message) {
        sendResult(
                routingContext.response(),
                trace,
                new JsonObject()
                        .put("statusCode", HttpStatus.BAD_REQUEST.value())
                        .put("data", new JsonObject())
                        .put("message", message)
        );
    }

    private Future<Void> sendResult(HttpServerResponse response, JsonObject trace, JsonObject result) {
        int statusCode = result.getInteger("statusCode", HttpStatus.OK.value());
        response.putHeader("content-type", CONTENT_TYPE);
        response.putHeader("Access-Control-Allow-Origin", "*");
        response.setStatusCode(statusCode);

        Future<Void> write = response.end(result.encodePrettily());
        if (trace != null) {
            write.onSuccess(ignored -> monitor.httpCompleted(trace, statusCode))
                    .onFailure(cause -> monitor.httpFailed(trace, cause));
        }
        return write;
    }
}
