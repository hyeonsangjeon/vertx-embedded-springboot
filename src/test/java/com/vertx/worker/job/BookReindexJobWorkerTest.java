package com.vertx.worker.job;

import com.vertx.worker.monitor.EventLoopMonitor;
import com.vertx.worker.mvc.dto.Book;
import com.vertx.worker.mvc.repository.BookRepository;
import com.vertx.worker.search.BookSearchIndex;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BookReindexJobWorkerTest {
    @Test
    void duplicateDeliveryDoesNotReadOrPublishTwice() throws Exception {
        BookJobRegistry registry = new BookJobRegistry(1, 8, 900);
        BookJob job = registry.submit("reindex", "key", BookReindexJobWorker.ADDRESS).job();
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger publications = new AtomicInteger();
        BookRepository repository = repository(() -> {
            reads.incrementAndGet();
            reading.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return List.of();
        });
        BookSearchIndex index = new BookSearchIndex() {
            @Override
            public void replace(List<IndexedBook> documents) {
                publications.incrementAndGet();
                super.replace(documents);
            }
        };
        BookReindexJobWorker worker = new BookReindexJobWorker(registry, repository, new EventLoopMonitor(), index, 0);
        JsonObject command = new JsonObject().put("jobId", job.getId());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> worker.reindex(command));
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            worker.reindex(command);
            assertEquals(1, reads.get());
            assertFalse(registry.markDispatchFailed(job.getId(), new IllegalStateException("late reply")));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            worker.reindex(command);
            assertEquals(1, reads.get());
            assertEquals(1, publications.get());
            assertEquals("COMPLETED", job.toJson().getString("status"));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void failedOrUnknownJobsCannotReachTheRepository() {
        BookJobRegistry registry = new BookJobRegistry(1, 8, 900);
        BookJob job = registry.submit("reindex", "key", BookReindexJobWorker.ADDRESS).job();
        registry.markDispatchFailed(job.getId(), new IllegalStateException("timeout"));
        BookReindexJobWorker worker = new BookReindexJobWorker(registry, repository(() -> {
            fail("a failed job must not read the database");
            return List.of();
        }), new EventLoopMonitor(), new BookSearchIndex(), 0);

        worker.reindex(new JsonObject().put("jobId", job.getId()));
        worker.reindex(new JsonObject().put("jobId", "unknown"));
        assertEquals("DISPATCH_FAILED", job.toJson().getString("status"));
    }

    @Test
    void repositoryFailureReleasesCapacityWithoutReplacingTheIndex() {
        BookJobRegistry registry = new BookJobRegistry(1, 8, 900);
        BookJob job = registry.submit("reindex", "key", BookReindexJobWorker.ADDRESS).job();
        BookSearchIndex index = new BookSearchIndex();
        index.replace(List.of(new BookSearchIndex.IndexedBook(1L, "old", "author", 1, "old author")));
        BookReindexJobWorker worker = new BookReindexJobWorker(registry, repository(() -> {
            throw new IllegalStateException("database unavailable");
        }), new EventLoopMonitor(), index, 0);

        worker.reindex(new JsonObject().put("jobId", job.getId()));
        assertEquals("FAILED", job.toJson().getString("status"));
        assertEquals(1, index.search("old").size());
        assertEquals(BookJobRegistry.Outcome.ACCEPTED,
                registry.submit("reindex", "next", BookReindexJobWorker.ADDRESS).outcome());
    }

    private BookRepository repository(Callable<Iterable<Book>> findAll) {
        return (BookRepository) Proxy.newProxyInstance(BookRepository.class.getClassLoader(),
                new Class<?>[]{BookRepository.class}, (proxy, method, args) -> {
                    if (method.getName().equals("findAll") && method.getParameterCount() == 0) {
                        return findAll.call();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
