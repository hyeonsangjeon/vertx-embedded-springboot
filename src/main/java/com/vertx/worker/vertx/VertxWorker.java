package com.vertx.worker.vertx;

import com.vertx.worker.job.BookReindexJobWorker;
import com.vertx.worker.job.BookJob;
import com.vertx.worker.job.BookJobRegistry;
import com.vertx.worker.monitor.EventLoopMonitor;
import com.vertx.worker.mvc.service.BookAsyncService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

/**
 * Worker-pool verticle that executes service calls and accepted background jobs off the event loop.
 */
@Component
@Scope(SCOPE_PROTOTYPE)
public class VertxWorker extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(VertxWorker.class);

    private final BookAsyncService bookAsyncService;
    private final BookReindexJobWorker bookReindexJobWorker;
    private final BookJobRegistry jobRegistry;
    private final EventLoopMonitor monitor;

    private MessageConsumer<JsonObject> serviceConsumer;
    private MessageConsumer<JsonObject> jobConsumer;

    public VertxWorker(
            BookAsyncService bookAsyncService,
            BookReindexJobWorker bookReindexJobWorker,
            BookJobRegistry jobRegistry,
            EventLoopMonitor monitor) {
        this.bookAsyncService = bookAsyncService;
        this.bookReindexJobWorker = bookReindexJobWorker;
        this.jobRegistry = jobRegistry;
        this.monitor = monitor;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        serviceConsumer = new ServiceBinder(vertx)
                .setAddress(BookAsyncService.ADDRESS)
                .register(BookAsyncService.class, bookAsyncService);

        jobConsumer = vertx.eventBus().consumer(
                BookReindexJobWorker.ADDRESS,
                message -> {
                    JsonObject command = message.body();
                    BookJob job = jobRegistry.find(command.getString("jobId")).orElse(null);
                    if (job == null) {
                        message.fail(404, "job not found or expired");
                        return;
                    }
                    monitor.jobDispatched(command.getJsonObject("trace"), job.toJson());
                    message.reply(new JsonObject()
                            .put("jobId", job.getId())
                            .put("received", true));
                    bookReindexJobWorker.reindex(command);
                }
        );

        Future.all(serviceConsumer.completion(), jobConsumer.completion()).onComplete(ar -> {
            if (ar.succeeded()) {
                logger.info("Vert.x worker consumer started on {}", Thread.currentThread().getName());
                startPromise.complete();
            } else {
                startPromise.fail(ar.cause());
            }
        });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        Future<Void> serviceClose = serviceConsumer == null ? Future.succeededFuture() : serviceConsumer.unregister();
        Future<Void> jobClose = jobConsumer == null ? Future.succeededFuture() : jobConsumer.unregister();

        Future.all(serviceClose, jobClose).onComplete(ar -> {
            if (ar.succeeded()) {
                stopPromise.complete();
            } else {
                stopPromise.fail(ar.cause());
            }
        });
    }
}
