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

    @Test
    void runningJobCannotBeClaimedAgainOrLoseProgress() {
        BookJob job = new BookJob("job-4", "book.search-index.rebuild");
        assertTrue(job.markRunning(4));
        job.markProgress(2, "half done");
        assertFalse(job.markRunning(0));
        job.markProgress(1, "late progress");
        assertEquals(2, job.toJson().getInteger("processed"));
        assertEquals(4, job.toJson().getInteger("total"));
    }

    @Test
    void terminalStatesCannotBeResurrectedOrOverwritten() {
        for (String terminal : new String[]{"COMPLETED", "FAILED", "DISPATCH_FAILED"}) {
            BookJob job = new BookJob(terminal, "book.search-index.rebuild");
            if (terminal.equals("DISPATCH_FAILED")) {
                job.markDispatchFailed(new IllegalStateException("no consumer"));
            } else {
                job.markRunning(3);
                if (terminal.equals("COMPLETED")) {
                    job.markCompleted("done", new JsonObject().put("count", 3));
                } else {
                    job.markFailed(new IllegalStateException("execution failed"));
                }
            }
            JsonObject finished = job.toJson();
            job.markDispatching();
            assertFalse(job.markRunning(0));
            job.setTotal(99);
            job.markProgress(99, "late progress");
            job.markCompleted("late completion", new JsonObject());
            job.markFailed(new IllegalStateException("late failure"));
            assertFalse(job.markDispatchFailed(new IllegalStateException("late timeout")));
            assertFalse(job.markResponseFailed(new IllegalStateException("late response failure")));
            assertEquals(finished, job.toJson());
        }
    }
}
