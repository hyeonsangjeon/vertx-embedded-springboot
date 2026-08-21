package com.vertx.worker.job;

import io.vertx.core.json.JsonObject;

import java.time.Duration;
import java.time.Instant;

public class BookJob {
    private final String id;
    private final String type;
    private final Instant createdAt;

    private Status status;
    private int total;
    private int processed;
    private String message;
    private String failureCode;
    private String error;
    private Instant startedAt;
    private Instant updatedAt;
    private Instant completedAt;
    private JsonObject result;

    BookJob(String id, String type) {
        this.id = id;
        this.type = type;
        this.status = Status.ACCEPTED;
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
                .put("status", status.name())
                .put("terminal", status.isTerminal())
                .put("total", total)
                .put("processed", processed)
                .put("progressPercent", progressPercent())
                .put("message", message)
                .put("createdAt", createdAt.toString())
                .put("updatedAt", updatedAt.toString())
                .put("elapsedMs", Duration.between(createdAt, updatedAt).toMillis());

        if (startedAt != null) {
            json.put("startedAt", startedAt.toString());
        }
        if (completedAt != null) {
            json.put("completedAt", completedAt.toString());
        }
        if (failureCode != null) {
            json.put("failureCode", failureCode);
        }
        if (error != null) {
            json.put("error", error);
        }
        if (result != null) {
            json.put("result", result.copy());
        }
        return json;
    }

    synchronized void markDispatching() {
        if (status != Status.ACCEPTED) {
            return;
        }
        this.status = Status.DISPATCHING;
        this.message = "dispatching job to a worker";
        this.updatedAt = Instant.now();
    }

    synchronized boolean markRunning(int total) {
        if (status.isTerminal()) {
            return false;
        }
        this.status = Status.RUNNING;
        this.total = Math.max(total, 0);
        this.processed = 0;
        this.message = "job started";
        this.startedAt = Instant.now();
        this.updatedAt = startedAt;
        return true;
    }

    synchronized void markProgress(int processed, String message) {
        this.status = Status.RUNNING;
        this.processed = Math.min(Math.max(processed, 0), total);
        this.message = message;
        this.updatedAt = Instant.now();
    }

    synchronized void markCompleted(String message, JsonObject result) {
        this.status = Status.COMPLETED;
        this.processed = total;
        this.message = message;
        this.result = result == null ? null : result.copy();
        this.completedAt = Instant.now();
        this.updatedAt = completedAt;
    }

    synchronized void markFailed(Throwable cause) {
        if (status.isTerminal()) {
            return;
        }
        this.status = Status.FAILED;
        this.message = "job failed";
        this.failureCode = "WORKER_EXECUTION_FAILED";
        this.error = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        this.completedAt = Instant.now();
        this.updatedAt = completedAt;
    }

    synchronized boolean markDispatchFailed(Throwable cause) {
        if (status != Status.ACCEPTED && status != Status.DISPATCHING) {
            return false;
        }
        this.status = Status.DISPATCH_FAILED;
        this.message = "worker dispatch failed";
        this.failureCode = "EVENT_BUS_DISPATCH_FAILED";
        this.error = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        this.completedAt = Instant.now();
        this.updatedAt = completedAt;
        return true;
    }

    private int progressPercent() {
        if (status == Status.COMPLETED) {
            return 100;
        }
        if (total == 0) {
            return 0;
        }
        return (int) Math.round(processed * 100.0 / total);
    }

    private enum Status {
        ACCEPTED,
        DISPATCHING,
        RUNNING,
        COMPLETED,
        FAILED,
        DISPATCH_FAILED;

        private boolean isTerminal() {
            return this == COMPLETED || this == FAILED || this == DISPATCH_FAILED;
        }
    }
}
