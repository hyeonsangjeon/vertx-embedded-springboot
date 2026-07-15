package com.vertx.worker;

import com.vertx.worker.vertx.VertxFacade;
import com.vertx.worker.vertx.VertxWorker;
import com.vertx.worker.vertx.factory.SpringVerticleFactory;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Bootstraps Vert.x inside the Spring application context.
 * Worker consumers are deployed before the HTTP facade so requests cannot arrive before a handler is ready.
 *
 * @author hyeonsang jeon
 */
@SpringBootApplication
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private final SpringVerticleFactory verticleFactory;

    private final int workerPoolSize;
    private final int springWorkerInstances;
    private final int maxEventLoopExecuteTime;
    private final int blockedThreadCheckInterval;

    private Vertx vertx;

    public Application(
            SpringVerticleFactory verticleFactory,
            @Value("${vertx.worker.pool.size}") int workerPoolSize,
            @Value("${vertx.springWorker.instances}") int springWorkerInstances,
            @Value("${vertx.max.eventloop.execute.time}") int maxEventLoopExecuteTime,
            @Value("${vertx.blocked.thread.check.interval}") int blockedThreadCheckInterval) {
        this.verticleFactory = verticleFactory;
        this.workerPoolSize = workerPoolSize;
        this.springWorkerInstances = springWorkerInstances;
        this.maxEventLoopExecuteTime = maxEventLoopExecuteTime;
        this.blockedThreadCheckInterval = blockedThreadCheckInterval;
    }
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void deployVerticles() {
        vertx = Vertx.builder().with(vertxOptions()).build();
        vertx.registerVerticleFactory(verticleFactory);

        DeploymentOptions workerDeployOpt = new DeploymentOptions()
                .setThreadingModel(ThreadingModel.WORKER)
                .setInstances(springWorkerInstances);
        String vertxWorker = verticleFactory.prefix() + ":" + VertxWorker.class.getName();
        String vertxFacade = verticleFactory.prefix() + ":" + VertxFacade.class.getName();

        Future<String> deployment = vertx.deployVerticle(vertxWorker, workerDeployOpt)
                .onSuccess(id -> logger.info("Deployed {} worker verticles", springWorkerInstances))
                .compose(ignored -> vertx.deployVerticle(vertxFacade))
                .onSuccess(id -> logger.info("Vert.x facade is ready"));

        try {
            await(deployment, "Vert.x startup");
        } catch (IllegalStateException e) {
            try {
                await(vertx.close(), "Vert.x startup cleanup");
            } catch (IllegalStateException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void closeVertx() {
        if (vertx == null) {
            return;
        }

        try {
            await(vertx.close(), "Vert.x shutdown");
        } catch (IllegalStateException e) {
            logger.warn("Failed to close Vert.x cleanly", e);
        }
    }

    private void await(Future<?> future, String operation) {
        try {
            future.toCompletionStage().toCompletableFuture().get(10, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(operation + " was interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(operation + " failed", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException(operation + " timed out", e);
        }
    }

    private VertxOptions vertxOptions() {
        return new VertxOptions()
                .setWorkerPoolSize(workerPoolSize)
                .setBlockedThreadCheckInterval(blockedThreadCheckInterval)
                .setBlockedThreadCheckIntervalUnit(MILLISECONDS)
                .setMaxEventLoopExecuteTime(maxEventLoopExecuteTime)
                .setMaxEventLoopExecuteTimeUnit(MILLISECONDS);
    }
}
