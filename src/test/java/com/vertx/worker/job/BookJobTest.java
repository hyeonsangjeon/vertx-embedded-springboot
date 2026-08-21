package com.vertx.worker.job;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookJobTest {

    @Test
    void exposesAcceptedRunningAndCompletedProgress() {
        BookJob job = new BookJob("job-1", "book.search-index.rebuild");

        JsonObject accepted = job.toJson();
        assertEquals("ACCEPTED", accepted.getString("status"));
        assertFalse(accepted.getBoolean("terminal"));
        assertEquals(0, accepted.getInteger("progressPercent"));
        assertFalse(accepted.containsKey("result"));

        job.markDispatching();
        assertEquals("DISPATCHING", job.toJson().getString("status"));

        job.markRunning(4);
        job.markProgress(1, "indexed book 1");
        assertEquals(25, job.toJson().getInteger("progressPercent"));

        job.markCompleted("search index published", new JsonObject().put("indexedDocuments", 4));
        JsonObject completed = job.toJson();
        assertEquals("COMPLETED", completed.getString("status"));
        assertTrue(completed.getBoolean("terminal"));
        assertEquals(100, completed.getInteger("progressPercent"));
        assertEquals(4, completed.getJsonObject("result").getInteger("indexedDocuments"));
    }

    @Test
    void recordsDispatchFailureAsATerminalState() {
        BookJob job = new BookJob("job-2", "book.search-index.rebuild");

        job.markDispatching();
        assertTrue(job.markDispatchFailed(new IllegalStateException("No handlers for worker address")));

        JsonObject failed = job.toJson();
        assertEquals("DISPATCH_FAILED", failed.getString("status"));
        assertTrue(failed.getBoolean("terminal"));
        assertEquals("EVENT_BUS_DISPATCH_FAILED", failed.getString("failureCode"));
        assertEquals("No handlers for worker address", failed.getString("error"));
        assertFalse(job.markRunning(1));
    }

    @Test
    void doesNotOverwriteAStartedJobWhenTheDispatchTimeoutArrivesLate() {
        BookJob job = new BookJob("job-3", "book.search-index.rebuild");

        job.markDispatching();
        assertTrue(job.markRunning(2));
        assertFalse(job.markDispatchFailed(new IllegalStateException("reply timed out")));

        assertEquals("RUNNING", job.toJson().getString("status"));
    }
}
