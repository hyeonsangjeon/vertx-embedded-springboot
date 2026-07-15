package com.vertx.worker.job;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BookJobTest {

    @Test
    void exposesAcceptedRunningAndCompletedProgress() {
        BookJob job = new BookJob("job-1", "book.search-index.rebuild");

        JsonObject accepted = job.toJson();
        assertEquals("ACCEPTED", accepted.getString("status"));
        assertEquals(0, accepted.getInteger("progressPercent"));
        assertFalse(accepted.containsKey("result"));

        job.markRunning(4);
        job.markProgress(1, "indexed book 1");
        assertEquals(25, job.toJson().getInteger("progressPercent"));

        job.markCompleted("search index published", new JsonObject().put("indexedDocuments", 4));
        JsonObject completed = job.toJson();
        assertEquals("COMPLETED", completed.getString("status"));
        assertEquals(100, completed.getInteger("progressPercent"));
        assertEquals(4, completed.getJsonObject("result").getInteger("indexedDocuments"));
    }
}
