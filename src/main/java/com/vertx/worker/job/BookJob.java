package com.vertx.worker.job;

import io.vertx.core.json.JsonObject;

import java.time.Instant;

public class BookJob {
    private final String id;
    private final String type;
    private final Instant createdAt;

    private String status;
    private int total;
    private int processed;
    private String message;
    private String error;
    private Instant startedAt;
    private Instant updatedAt;
    private Instant completedAt;

    BookJob(String id, String type) {
        this.id = id;
        this.type = type;
        this.status = "ACCEPTED";
        this.message = "job accepted";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public synchronized JsonObject toJson() {
        JsonObject json = new JsonObject()
                .put("jobId", id)
                .put("type", type)
                .put("status", status)
                .put("total", total)
                .put("processed", processed)
                .put("message", message)
                .put("createdAt", createdAt.toString())
                .put("updatedAt", updatedAt.toString());

        if (startedAt != null) {
            json.put("startedAt", startedAt.toString());
        }
        if (completedAt != null) {
            json.put("completedAt", completedAt.toString());
        }
        if (error != null) {
            json.put("error", error);
        }
        return json;
    }

    synchronized void markRunning(int total) {
        this.status = "RUNNING";
        this.total = total;
        this.processed = 0;
        this.message = "job started";
        this.startedAt = Instant.now();
        this.updatedAt = startedAt;
    }

    synchronized void markProgress(int processed, String message) {
        this.status = "RUNNING";
        this.processed = processed;
        this.message = message;
        this.updatedAt = Instant.now();
    }

    synchronized void markCompleted(String message) {
        this.status = "COMPLETED";
        this.processed = total;
        this.message = message;
        this.completedAt = Instant.now();
        this.updatedAt = completedAt;
    }

    synchronized void markFailed(Throwable cause) {
        this.status = "FAILED";
        this.message = "job failed";
        this.error = cause.getMessage();
        this.completedAt = Instant.now();
        this.updatedAt = completedAt;
    }
}
