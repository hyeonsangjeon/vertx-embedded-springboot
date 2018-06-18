package com.vertx.worker;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import com.vertx.worker.mvc.repository.BookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import com.vertx.worker.vertx.factory.SpringVerticleFactory;
import com.vertx.worker.vertx.VertxFacade;
import com.vertx.worker.vertx.VertxWorker;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


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

    @Autowired
    SpringVerticleFactory verticleFactory;

    /**
     * The Vert.x worker pool size, configured in the {@code application.properties} file.
     * Make sure this is greater than {@link #springWorkerInstances}.
     */
    @Value("${vertx.worker.pool.size}")
    int workerPoolSize;


    /**
     * The number of {@link VertxWorker} instances to deploy, configured in the {@code application.properties} file.
     */
    @Value("${vertx.springWorker.instances}")
    int springWorkerInstances;


    @Value("${vertx.max.eventloop.execute.time}")
    int maxEventLoopExecuteTime;

    @Value("${vertx.blocked.threa.check.interval}")
    int blockedThreadCheckInterval;


    public static void main(String[] args) throws Exception {
        SpringApplication.run(new Object[]{Application.class}, args);
    }


    //After Spring application is ready, verticles deploying starts.
    @EventListener
    public void deployVerticles(ApplicationReadyEvent event) {
        VertxOptions options = new VertxOptions();
        options.setWorkerPoolSize(workerPoolSize); //worker pool size
        options.setBlockedThreadCheckInterval(blockedThreadCheckInterval);
        options.setMaxEventLoopExecuteTime(maxEventLoopExecuteTime);

        Vertx vertx = Vertx.vertx(options);

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
        DeploymentOptions workerDeployOpt = new DeploymentOptions().setWorker(true).setInstances(springWorkerInstances);
        String vertxWorker = verticleFactory.prefix() + ":" + VertxWorker.class.getName();

        vertx.deployVerticle(vertxWorker, workerDeployOpt, res -> {
            if (res.succeeded()) {
                deployLatch.countDown();
            } else {
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
            throw new RuntimeException(e);
        }

    }


}
