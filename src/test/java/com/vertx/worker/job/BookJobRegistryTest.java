package com.vertx.worker.job;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.vertx.worker.job.BookJobRegistry.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

class BookJobRegistryTest {
    private static final String TYPE = "book.search-index.rebuild";
    private static final String ADDRESS = BookReindexJobWorker.ADDRESS;

    @Test
    void concurrentRetriesCreateOneJobEvenWhenActiveCapacityIsFull() throws Exception {
        BookJobRegistry registry = new BookJobRegistry(1, 8, 900);
        List<BookJobRegistry.Admission> results = race(IntStream.range(0, 16)
                .<Callable<BookJobRegistry.Admission>>mapToObj(i -> () -> registry.submit(TYPE, "same-key", ADDRESS))
                .toList());

        assertEquals(1, results.stream().filter(result -> result.outcome() == ACCEPTED).count());
        assertEquals(15, results.stream().filter(result -> result.outcome() == REPLAYED).count());
        assertEquals(1, results.stream().map(result -> result.job().getId()).distinct().count());
    }

    @Test
    void concurrentNewKeysCannotExceedTheActiveLimit() throws Exception {
        BookJobRegistry registry = new BookJobRegistry(2, 8, 900);
        List<BookJobRegistry.Admission> results = race(IntStream.range(0, 16)
                .<Callable<BookJobRegistry.Admission>>mapToObj(i -> () -> registry.submit(TYPE, "key-" + i, ADDRESS))
                .toList());

        assertEquals(2, results.stream().filter(result -> result.outcome() == ACCEPTED).count());
        assertEquals(14, results.stream().filter(result -> result.outcome() == ACTIVE_LIMIT).count());
        assertTrue(results.stream().filter(result -> result.outcome() == ACTIVE_LIMIT)
                .allMatch(result -> result.job() == null));
    }

    @Test
    void rejectsKeyReuseForDifferentWorkBeforeCheckingCapacity() {
        BookJobRegistry registry = new BookJobRegistry(1, 8, 900);
        registry.submit(TYPE, "key", ADDRESS);

        assertEquals(KEY_CONFLICT, registry.submit(TYPE, "key", ADDRESS + ".missing").outcome());
        assertEquals(KEY_CONFLICT, registry.submit("different-type", "key", ADDRESS).outcome());
        assertEquals(REPLAYED, registry.submit(TYPE, "key", ADDRESS).outcome());
    }

    @Test
    void preservesRetainedKeysRatherThanEvictingThemToAdmitNewWork() {
        MutableClock clock = new MutableClock();
        BookJobRegistry registry = new BookJobRegistry(1, 1, 60, clock);
        BookJob first = registry.submit(TYPE, "first", ADDRESS).job();
        first.markRunning(0);
        clock.advance(30);
        first.markCompleted("done", null);
        clock.advance(59);

        assertEquals(HISTORY_LIMIT, registry.submit(TYPE, "second", ADDRESS).outcome());
        assertSame(first, registry.submit(TYPE, "first", ADDRESS).job());
        assertEquals(REPLAYED, registry.submit(TYPE, "first", ADDRESS).outcome());
        assertTrue(registry.find(first.getId()).isPresent());

        clock.advance(1);
        assertTrue(registry.find(first.getId()).isEmpty());
        BookJobRegistry.Admission newUseOfKey = registry.submit(TYPE, "first", ADDRESS);
        assertEquals(ACCEPTED, newUseOfKey.outcome());
        assertNotEquals(first.getId(), newUseOfKey.job().getId());
    }

    @Test
    void admissionAlsoRemovesExpiredRecordsAndKeys() {
        MutableClock clock = new MutableClock();
        BookJobRegistry registry = new BookJobRegistry(1, 1, 60, clock);
        BookJob first = registry.submit(TYPE, "first", ADDRESS).job();
        first.markFailed(new IllegalStateException("worker failed"));
        clock.advance(60);

        assertEquals(ACCEPTED, registry.submit(TYPE, "first", ADDRESS + ".missing").outcome());
        assertTrue(registry.find(first.getId()).isEmpty());
        assertFalse(registry.markDispatchFailed(first.getId(), new IllegalStateException("late timeout")));
    }

    @Test
    void neverExpiresAcceptedDispatchingOrRunningWork() {
        MutableClock clock = new MutableClock();
        BookJobRegistry registry = new BookJobRegistry(3, 3, 60, clock);
        BookJob accepted = registry.submit(TYPE, "accepted", ADDRESS).job();
        BookJob dispatching = registry.submit(TYPE, "dispatching", ADDRESS).job();
        dispatching.markDispatching();
        BookJob running = registry.submit(TYPE, "running", ADDRESS).job();
        running.markRunning(1);
        clock.advance(10_000);

        for (BookJob job : List.of(accepted, dispatching, running)) {
            assertSame(job, registry.find(job.getId()).orElseThrow());
        }
        assertEquals(ACTIVE_LIMIT, registry.submit(TYPE, "new", ADDRESS).outcome());
    }

    @Test
    void terminalFailuresReleaseActiveCapacityAndRemainReplayable() {
        BookJobRegistry registry = new BookJobRegistry(1, 8, 900);
        BookJob first = registry.submit(TYPE, "dispatch", ADDRESS).job();
        registry.markDispatching(first.getId());
        assertTrue(registry.markDispatchFailed(first.getId(), new IllegalStateException("no consumer")));

        BookJob second = registry.submit(TYPE, "response", ADDRESS).job();
        assertNotNull(second);
        assertTrue(registry.markResponseFailed(second.getId(), new IllegalStateException("connection closed")));
        assertEquals("HTTP_RESPONSE_FAILED", second.toJson().getString("failureCode"));
        assertEquals(REPLAYED, registry.submit(TYPE, "response", ADDRESS).outcome());

        BookJob third = registry.submit(TYPE, "execution", ADDRESS).job();
        assertNotNull(third);
        third.markRunning(1);
        third.markFailed(new IllegalStateException("database unavailable"));
        assertEquals(ACCEPTED, registry.submit(TYPE, null, ADDRESS).outcome());
    }

    @Test
    void submissionsWithoutKeysRemainIndependent() {
        BookJobRegistry registry = new BookJobRegistry(2, 8, 900);
        BookJob first = registry.submit(TYPE, null, ADDRESS).job();
        BookJob second = registry.submit(TYPE, null, ADDRESS).job();
        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void validatesKeysAndLimitsBeforeAllocatingState() {
        BookJobRegistry registry = new BookJobRegistry(1, 8, 900);
        for (String invalid : List.of("", " ", "two words", "a/b", "a".repeat(129), "caf\u00e9")) {
            assertThrows(IllegalArgumentException.class, () -> registry.submit(TYPE, invalid, ADDRESS));
        }
        assertEquals(ACCEPTED, registry.submit(TYPE, "a".repeat(128), ADDRESS).outcome());
        assertThrows(IllegalArgumentException.class, () -> new BookJobRegistry(0, 8, 900));
        assertThrows(IllegalArgumentException.class, () -> new BookJobRegistry(2, 1, 900));
        assertThrows(IllegalArgumentException.class, () -> new BookJobRegistry(1, 8, 0));
    }

    private <T> List<T> race(List<Callable<T>> actions) throws Exception {
        var executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            var futures = actions.stream().map(action -> executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return action.call();
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<T> results = new ArrayList<>();
            for (var future : futures) {
                results.add(future.get(5, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-02T00:00:00Z");

        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
