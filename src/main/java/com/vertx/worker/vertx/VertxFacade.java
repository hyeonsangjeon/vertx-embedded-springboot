package com.vertx.worker.vertx;

import com.vertx.worker.job.BookJobRegistry;
import com.vertx.worker.monitor.EventLoopMonitor;
import com.vertx.worker.mvc.handler.RouteHandler;
import com.vertx.worker.mvc.service.BookAsyncService;
import com.vertx.worker.search.BookSearchIndex;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.serviceproxy.ServiceProxyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Event-loop boundary for HTTP requests.
 * It routes lightweight work to {@link BookAsyncService} and accepts background jobs through {@link RouteHandler}.
 */
@Component
public class VertxFacade extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(VertxFacade.class);

    private final int vertxPort;
    private final EventLoopMonitor monitor;
    private final BookJobRegistry jobRegistry;
    private final BookSearchIndex searchIndex;

    private BookAsyncService bookAsyncService;

    public VertxFacade(
            @Value("${vertx.port}") int vertxPort,
            EventLoopMonitor monitor,
            BookJobRegistry jobRegistry,
            BookSearchIndex searchIndex) {
        this.vertxPort = vertxPort;
        this.monitor = monitor;
        this.jobRegistry = jobRegistry;
        this.searchIndex = searchIndex;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        bookAsyncService = new ServiceProxyBuilder(vertx).setAddress(BookAsyncService.ADDRESS).build(BookAsyncService.class);

        monitor.bind(vertx)
                .compose(ignored -> startServer())
                .onSuccess(http -> {
                    logger.info("Vert.x HTTP server started on port {}", http.actualPort());
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }

    private Future<HttpServer> startServer() {
        Router apiRouter = Router.router(vertx);
        apiRouter.get("/").handler(this::describeApi);
        apiRouter.route("/book/*").subRouter(
                new RouteHandler(vertx, bookAsyncService, monitor, jobRegistry, searchIndex).getRouter()
        );
        return vertx.createHttpServer()
                .requestHandler(apiRouter)
                .listen(vertxPort);
    }

    private void describeApi(RoutingContext routingContext) {
        JsonObject response = new JsonObject()
                .put("name", "Vert.x Embedded Spring Boot Async Worker Pattern")
                .put("status", "ready")
                .put("pattern", "accept on event loop -> dispatch over event bus -> execute on worker -> observe")
                .put("servedByThread", Thread.currentThread().getName())
                .put("try", new JsonObject()
                        .put("watchEvents", "GET /book/events")
                        .put("submitJob", "POST /book/jobs/reindex")
                        .put("jobStatus", "GET /book/jobs/{jobId}")
                        .put("searchResult", "GET /book/search?q=Hyeon-Sang"));

        routingContext.response()
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(response.encodePrettily());
    }
}
