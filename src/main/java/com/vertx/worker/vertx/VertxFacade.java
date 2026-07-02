package com.vertx.worker.vertx;

import com.vertx.worker.job.BookJobRegistry;
import com.vertx.worker.monitor.EventLoopMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.vertx.worker.mvc.handler.RouteHandler;
import com.vertx.worker.mvc.service.BookAsyncService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.serviceproxy.ServiceProxyBuilder;

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

    private BookAsyncService bookAsyncService;

    public VertxFacade(@Value("${vertx.port}") int vertxPort, EventLoopMonitor monitor, BookJobRegistry jobRegistry) {
        this.vertxPort = vertxPort;
        this.monitor = monitor;
        this.jobRegistry = jobRegistry;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        bookAsyncService = new ServiceProxyBuilder(vertx).setAddress(BookAsyncService.ADDRESS).build(BookAsyncService.class);

        monitor.bind(vertx)
                .compose(ignored -> startServer())
                .onComplete(http -> completeStartup(http, startPromise));
    }

    private Future<HttpServer> startServer() {
        Router apiRouter = Router.router(vertx);
        apiRouter.route("/book/*").subRouter(new RouteHandler(vertx, bookAsyncService, monitor, jobRegistry).getRouter());
        return vertx.createHttpServer()
                .requestHandler(apiRouter)
                .listen(vertxPort);
    }

    private void completeStartup(AsyncResult<HttpServer> http, Promise<Void> promise) {
        if (http.succeeded()) {
            logger.info("Vert.x HTTP server started on port {}", vertxPort);
            promise.complete();
        } else {
            promise.fail(http.cause());
        }
    }

}
