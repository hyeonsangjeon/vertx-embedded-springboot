package com.vertx.worker.job;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class BookJobRegistry {
    private final ConcurrentMap<String, BookJob> jobs = new ConcurrentHashMap<>();

    public BookJob accept(String type) {
        BookJob job = new BookJob(UUID.randomUUID().toString(), type);
        jobs.put(job.getId(), job);
        return job;
    }

    public Optional<BookJob> find(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public BookJob require(String jobId) {
        return find(jobId).orElseThrow(() -> new IllegalArgumentException("Unknown job: " + jobId));
    }
}
