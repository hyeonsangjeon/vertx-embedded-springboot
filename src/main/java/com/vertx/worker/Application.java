package com.vertx.worker;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import com.vertx.worker.vertx.factory.SpringVerticleFactory;
import com.vertx.worker.vertx.VertxFacade;
import com.vertx.worker.vertx.VertxWorker;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import static java.util.concurrent.TimeUnit.*;

/**
 * SpringBoot Building class ,building embeded springboot verticle
 * This class deploys  verticle instances which has single facade vertical {@link VertxFacade}  instance and multithreading process instances {@link VertxWorker}
 * Deploy verticles when the Spring application is ready.
 *
 * @author hyeonsang jeon
 */
@SpringBootApplication
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private final SpringVerticleFactory verticleFactory;

    /**
     * The Vert.x worker pool size, configured in the {@code application.properties} file.
     * Make sure this is greater than {@link #springWorkerInstances}.
     */
    private final int workerPoolSize;


    /**
     * The number of {@link VertxWorker} instances to deploy, configured in the {@code application.properties} file.
     */
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


    //After Spring application is ready, verticles deploying starts.
    @EventListener
    public void deployVerticles(ApplicationReadyEvent event) {
        vertx = Vertx.vertx(vertxOptions());
        vertx.registerVerticleFactory(verticleFactory);

        CountDownLatch deployLatch = new CountDownLatch(2);
        AtomicBoolean failed = new AtomicBoolean(false);

        //Sender(Handler)
        String vertxController = verticleFactory.prefix() + ":" + VertxFacade.class.getName();

        vertx.deployVerticle(vertxController, res -> {
            if (res.failed()) {
                logger.error("Failed to deploy verticle", res.cause());
                failed.compareAndSet(false, true);
            }
            deployLatch.countDown();
        });


        //Worker(Concrete)
        /**Worker Verticle
         * The number of worker instances is set larger than the minimum cpu cores. */
        DeploymentOptions workerDeployOpt = new DeploymentOptions()
                .setThreadingModel(ThreadingModel.WORKER)
                .setInstances(springWorkerInstances);
        String vertxWorker = verticleFactory.prefix() + ":" + VertxWorker.class.getName();

        vertx.deployVerticle(vertxWorker, workerDeployOpt, res -> {
            if (res.failed()) {
                logger.error("Failed to deploy verticle", res.cause());
                failed.compareAndSet(false, true);
            }
            deployLatch.countDown();
        });

        try {
            if (!deployLatch.await(5, SECONDS)) {
                throw new RuntimeException("Timeout waiting for verticle deployments");
            } else if (failed.get()) {
                throw new RuntimeException("Failure while deploying verticles");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

    }

    @EventListener
    public void closeVertx(ContextClosedEvent event) {
        if (vertx == null) {
            return;
        }

        CountDownLatch closeLatch = new CountDownLatch(1);
        vertx.close(ar -> {
            if (ar.failed()) {
                logger.warn("Failed to close Vert.x", ar.cause());
            }
            closeLatch.countDown();
        });

        try {
            if (!closeLatch.await(5, SECONDS)) {
                logger.warn("Timeout waiting for Vert.x shutdown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for Vert.x shutdown", e);
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
