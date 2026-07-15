package com.vertx.worker.mvc.handler;

import com.vertx.worker.job.BookJobRegistry;
import com.vertx.worker.monitor.EventLoopMonitor;
import com.vertx.worker.mvc.service.BookAsyncService;
import com.vertx.worker.search.BookSearchIndex;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

/** Defines the public book, job, search, and SSE routes. */
public class RouteHandler {
    private final Vertx vertx;
    private final BookAsyncService bookAsyncService;
    private final EventLoopMonitor monitor;
    private final BookJobRegistry jobRegistry;
    private final BookSearchIndex searchIndex;

    public RouteHandler(Vertx vertx, BookAsyncService bookAsyncService, EventLoopMonitor monitor,
                        BookJobRegistry jobRegistry, BookSearchIndex searchIndex) {
        this.vertx = vertx;
        this.bookAsyncService = bookAsyncService;
        this.monitor = monitor;
        this.jobRegistry = jobRegistry;
        this.searchIndex = searchIndex;
    }

    public Router getRouter() {
        RequestHandler reqHandler = new RequestHandler(bookAsyncService, monitor, jobRegistry, searchIndex, vertx);
        Router router = Router.router(vertx);

        router.get("/events").handler(reqHandler::events);

        router.route().handler(BodyHandler.create());
        router.route().produces("application/json");

        router.post("/add").handler(reqHandler::createBook);
        router.get("/list").handler(reqHandler::getAll);
        router.get("/id/:bookId").handler(reqHandler::get);
        router.get("/search").handler(reqHandler::searchBooks);
        router.put("/update").handler(reqHandler::updateBook);
        router.delete("/delete/:bookId").handler(reqHandler::deleteBook);
        router.post("/jobs/reindex").handler(reqHandler::createReindexJob);
        router.get("/jobs/:jobId").handler(reqHandler::getJob);
        router.route().failureHandler(reqHandler::failure);

        return router;
    }
}
