package com.vertx.worker.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class BookJobRegistry {
    private final Map<String, StoredJob> jobs = new HashMap<>();
    private final Map<String, StoredJob> keys = new HashMap<>();
    private final int maxActive;
    private final int maxRetained;
    private final Duration retention;
    private final Clock clock;

    @Autowired
    public BookJobRegistry(
            @Value("${demo.jobs.max-active:1}") int maxActive,
            @Value("${demo.jobs.max-retained:256}") int maxRetained,
            @Value("${demo.jobs.retention-seconds:900}") long retentionSeconds) {
        this(maxActive, maxRetained, retentionSeconds, Clock.systemUTC());
    }

    BookJobRegistry(int maxActive, int maxRetained, long retentionSeconds, Clock clock) {
        if (maxActive < 1 || maxRetained < maxActive || retentionSeconds < 1) {
            throw new IllegalArgumentException("Job limits require 1 <= max-active <= max-retained and retention-seconds >= 1");
        }
        this.maxActive = maxActive;
        this.maxRetained = maxRetained;
        this.retention = Duration.ofSeconds(retentionSeconds);
        this.clock = clock;
    }

    // Key lookup and admission share one short metadata-only critical section.
    public synchronized Admission submit(String type, String key, String dispatchAddress) {
        if (key != null && !key.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1-128 ASCII letters, digits, dots, underscores, colons, or hyphens");
        }
        removeExpired();
        RequestIdentity identity = new RequestIdentity(type, dispatchAddress);
        StoredJob existing = key == null ? null : keys.get(key);
        if (existing != null) {
            return existing.identity().equals(identity)
                    ? new Admission(Outcome.REPLAYED, existing.job())
                    : new Admission(Outcome.KEY_CONFLICT, null);
        }
        long active = jobs.values().stream().filter(stored -> !stored.job().isTerminal()).count();
        if (active >= maxActive) {
            return new Admission(Outcome.ACTIVE_LIMIT, null);
        }
        if (jobs.size() >= maxRetained) {
            return new Admission(Outcome.HISTORY_LIMIT, null);
        }

        BookJob job = new BookJob(UUID.randomUUID().toString(), type, clock);
        StoredJob stored = new StoredJob(job, key, identity);
        jobs.put(job.getId(), stored);
        if (key != null) {
            keys.put(key, stored);
        }
        return new Admission(Outcome.ACCEPTED, job);
    }

    public synchronized Optional<BookJob> find(String jobId) {
        removeExpired();
        return Optional.ofNullable(jobs.get(jobId)).map(StoredJob::job);
    }

    public BookJob require(String jobId) {
        return find(jobId).orElseThrow(() -> new IllegalArgumentException("Unknown job: " + jobId));
    }

    public BookJob markDispatching(String jobId) {
        BookJob job = require(jobId);
        job.markDispatching();
        return job;
    }

    public boolean markDispatchFailed(String jobId, Throwable cause) {
        return find(jobId).map(job -> job.markDispatchFailed(cause)).orElse(false);
    }

    public boolean markResponseFailed(String jobId, Throwable cause) {
        return find(jobId).map(job -> job.markResponseFailed(cause)).orElse(false);
    }

    private void removeExpired() {
        Instant cutoff = clock.instant().minus(retention);
        jobs.values().removeIf(stored -> {
            if (!stored.job().expiredAt(cutoff)) {
                return false;
            }
            if (stored.key() != null) {
                keys.remove(stored.key());
            }
            return true;
        });
    }

    public enum Outcome {
        ACCEPTED, REPLAYED, KEY_CONFLICT, ACTIVE_LIMIT, HISTORY_LIMIT
    }

    public record Admission(Outcome outcome, BookJob job) {
    }

    private record RequestIdentity(String type, String dispatchAddress) {
    }

    private record StoredJob(BookJob job, String key, RequestIdentity identity) {
    }
}
