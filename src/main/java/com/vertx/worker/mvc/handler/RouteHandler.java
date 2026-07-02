package com.vertx.worker.mvc.handler;

import com.vertx.worker.job.BookJobRegistry;
import com.vertx.worker.monitor.EventLoopMonitor;
import com.vertx.worker.mvc.service.BookAsyncService;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;


/**
 * A Router, class receives work through {@link RouteHandler}  class instance.
 *
 * @author hyeonsang jeon
 */
public class RouteHandler {


    private final Vertx vertx;
    private final BookAsyncService bookAsyncService;
    private final EventLoopMonitor monitor;
    private final BookJobRegistry jobRegistry;

    public RouteHandler(Vertx vertx, BookAsyncService bookAsyncService, EventLoopMonitor monitor,
                        BookJobRegistry jobRegistry) {
        this.vertx = vertx;
        this.bookAsyncService = bookAsyncService;
        this.monitor = monitor;
        this.jobRegistry = jobRegistry;
    }

    public Router getRouter() {
        RequestHandler reqHandler = new RequestHandler(bookAsyncService, monitor, jobRegistry, vertx);
        Router router = Router.router(vertx);

        router.get("/events").handler(reqHandler::events);

        router.route().handler(BodyHandler.create());
        router.route().produces("application/json");

        router.post("/add").handler(reqHandler::createBook);
        router.get("/list").handler(reqHandler::getAll);
        router.get("/id/:bookId").handler(reqHandler::get);
        router.put("/update").handler(reqHandler::updateBook);
        router.delete("/delete/:bookId").handler(reqHandler::deleteBook);
        router.post("/jobs/reindex").handler(reqHandler::createReindexJob);
        router.get("/jobs/:jobId").handler(reqHandler::getJob);

        return router;
    }


}
