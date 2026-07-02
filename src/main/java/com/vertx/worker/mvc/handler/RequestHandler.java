package com.vertx.worker.mvc.handler;

import com.vertx.worker.job.BookJob;
import com.vertx.worker.job.BookJobRegistry;
import com.vertx.worker.job.BookReindexJobWorker;
import com.vertx.worker.monitor.EventLoopMonitor;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import com.vertx.worker.mvc.dto.Book;
import com.vertx.worker.mvc.service.BookAsyncService;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.springframework.http.HttpStatus;

/**
 * HTTP handler that keeps event-loop work small and dispatches service calls or accepted jobs asynchronously.
 */
public class RequestHandler {

    private static final String CONTENT_TYPE = "application/json; charset=utf-8";

    private final BookAsyncService bookAsyncService;
    private final EventLoopMonitor monitor;
    private final BookJobRegistry jobRegistry;
    private final Vertx vertx;

    public RequestHandler(BookAsyncService bookAsyncService, EventLoopMonitor monitor, BookJobRegistry jobRegistry,
                          Vertx vertx) {
        this.bookAsyncService = bookAsyncService;
        this.monitor = monitor;
        this.jobRegistry = jobRegistry;
        this.vertx = vertx;
    }

    //Create Book
    public void createBook(RoutingContext routingContext) {
        JsonObject trace = monitor.startHttpRequest(routingContext, "book.create");
        JsonObject reqParam = requestBody(routingContext);
        if (reqParam == null) {
            monitor.httpCompleted(trace, HttpStatus.BAD_REQUEST.value());
            return;
        }

        dispatch(trace, "book.save");
        bookAsyncService.save(reqParam, trace, ar -> sendAsyncResult(routingContext, trace, ar));
    }


    //Read All Book List
    public void getAll(RoutingContext routingContext) {
        JsonObject trace = monitor.startHttpRequest(routingContext, "book.list");

        dispatch(trace, "book.list");
        bookAsyncService.getAll(trace, ar -> sendAsyncResult(routingContext, trace, ar));
    }


    //Read One Book
    public void get(RoutingContext routingContext) {
        JsonObject trace = monitor.startHttpRequest(routingContext, "book.get");
        Long bookId = bookIdParam(routingContext);
        if (bookId == null) {
            monitor.httpCompleted(trace, HttpStatus.BAD_REQUEST.value());
            return;
        }

        dispatch(trace, "book.get");
        bookAsyncService.get(bookId, trace, ar -> sendAsyncResult(routingContext, trace, ar));
    }

    //Update Book
    public void updateBook(RoutingContext routingContext) {
        JsonObject trace = monitor.startHttpRequest(routingContext, "book.update");
        JsonObject body = requestBody(routingContext);
        if (body == null) {
            monitor.httpCompleted(trace, HttpStatus.BAD_REQUEST.value());
            return;
        }

        Book reqBook = new Book(body);
        if (reqBook.getId() == null) {
            sendBadRequest(routingContext, "book id is required");
            monitor.httpCompleted(trace, HttpStatus.BAD_REQUEST.value());
            return;
        }

        dispatch(trace, "book.get");
        bookAsyncService.get(reqBook.getId(), trace, ar1st -> {
            if (ar1st.failed()) {
                monitor.httpFailed(trace, ar1st.cause());
                routingContext.fail(ar1st.cause());
            } else if (hasDataId(ar1st.result())) {
                dispatch(trace, "book.update");
                bookAsyncService.update(reqBook, trace, ar2nd -> sendAsyncResult(routingContext, trace, ar2nd));
            } else {
                this.sendResult(routingContext.response(), trace, ar1st.result());
            }
        });

    }

    //Delete Book
    public void deleteBook(RoutingContext routingContext) {
        JsonObject trace = monitor.startHttpRequest(routingContext, "book.delete");
        Long bookId = bookIdParam(routingContext);
        if (bookId == null) {
            monitor.httpCompleted(trace, HttpStatus.BAD_REQUEST.value());
            return;
        }

        dispatch(trace, "book.get");
        bookAsyncService.get(bookId, trace, ar1st -> {
            if (ar1st.failed()) {
                monitor.httpFailed(trace, ar1st.cause());
                routingContext.fail(ar1st.cause());
            } else if (hasDataId(ar1st.result())) {
                dispatch(trace, "book.delete");
                bookAsyncService.delete(bookId, trace, ar2nd -> {
                    if (ar2nd.succeeded()) {
                        JsonObject result = ar2nd.result().copy();
                        result.put("data", ar1st.result().getJsonObject("data"));
                        this.sendResult(routingContext.response(), trace, result);
                    } else {
                        monitor.httpFailed(trace, ar2nd.cause());
                        routingContext.fail(ar2nd.cause());
                    }
                });
            } else {
                this.sendResult(routingContext.response(), trace, ar1st.result());
            }

        });

    }

    public void events(RoutingContext routingContext) {
        monitor.connect(routingContext);
    }

    public void createReindexJob(RoutingContext routingContext) {
        JsonObject trace = monitor.startHttpRequest(routingContext, "job.reindex");
        BookJob job = jobRegistry.accept("book.reindex");
        JsonObject jobJson = job.toJson();

        monitor.jobAccepted(trace, jobJson);
        dispatch(trace, "job.reindex");
        vertx.eventBus().send(BookReindexJobWorker.ADDRESS, new JsonObject()
                .put("jobId", job.getId())
                .put("trace", trace));

        sendResult(
                routingContext.response(),
                trace,
                new JsonObject()
                        .put("statusCode", HttpStatus.ACCEPTED.value())
                        .put("data", jobJson)
                        .put("message", "job accepted")
        );
    }

    public void getJob(RoutingContext routingContext) {
        JsonObject trace = monitor.startHttpRequest(routingContext, "job.get");
        String jobId = routingContext.pathParam("jobId");

        JsonObject result = jobRegistry.find(jobId)
                .map(job -> new JsonObject()
                        .put("statusCode", HttpStatus.OK.value())
                        .put("data", job.toJson())
                        .put("message", "job status"))
                .orElseGet(() -> new JsonObject()
                        .put("statusCode", HttpStatus.NOT_FOUND.value())
                        .put("data", new JsonObject())
                        .put("message", "job not found"));

        sendResult(routingContext.response(), trace, result);
    }

    private JsonObject requestBody(RoutingContext routingContext) {
        try {
            JsonObject body = routingContext.body().asJsonObject();
            if (body == null) {
                sendBadRequest(routingContext, "JSON body is required");
            }
            return body;
        } catch (RuntimeException e) {
            sendBadRequest(routingContext, "invalid JSON body");
            return null;
        }
    }

    private Long bookIdParam(RoutingContext routingContext) {
        String bookId = routingContext.pathParam("bookId");
        try {
            return Long.parseLong(bookId);
        } catch (NumberFormatException e) {
            sendBadRequest(routingContext, "bookId must be a number");
            return null;
        }
    }

    private void dispatch(JsonObject trace, String step) {
        monitor.dispatchToWorker(trace, step);
    }

    private void sendAsyncResult(RoutingContext routingContext, JsonObject trace, AsyncResult<JsonObject> result) {
        if (result.succeeded()) {
            this.sendResult(routingContext.response(), trace, result.result());
        } else {
            monitor.httpFailed(trace, result.cause());
            routingContext.fail(result.cause());
        }
    }

    private boolean hasDataId(JsonObject result) {
        JsonObject data = result.getJsonObject("data");
        return data != null && data.getValue("id") != null;
    }

    private void sendBadRequest(RoutingContext routingContext, String message) {
        sendResult(
                routingContext.response(),
                null,
                new JsonObject()
                        .put("statusCode", HttpStatus.BAD_REQUEST.value())
                        .put("data", new JsonObject())
                        .put("message", message)
        );
    }

    //response method
    private void sendResult(HttpServerResponse response, JsonObject trace, JsonObject result) {
        int statusCode = result.getInteger("statusCode");
        response.putHeader("content-type", CONTENT_TYPE);
        response.putHeader("Access-Control-Allow-Origin", "*");
        response.putHeader("X-Application-Context", "application");
        response.setStatusCode(statusCode);
        response.end(result.encodePrettily());
        if (trace != null) {
            monitor.httpCompleted(trace, statusCode);
        }
    }

}
