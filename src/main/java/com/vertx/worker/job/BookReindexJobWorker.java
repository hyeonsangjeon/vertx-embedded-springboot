package com.vertx.worker.job;

import com.vertx.worker.monitor.EventLoopMonitor;
import com.vertx.worker.mvc.dto.Book;
import com.vertx.worker.mvc.repository.BookRepository;
import io.vertx.core.json.JsonObject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Sample long-running platform job executed from the Vert.x worker pool after the HTTP request is accepted.
 */
@Component
public class BookReindexJobWorker {
    public static final String ADDRESS = BookReindexJobWorker.class.getName();

    private final BookJobRegistry jobRegistry;
    private final BookRepository bookRepository;
    private final EventLoopMonitor monitor;

    public BookReindexJobWorker(BookJobRegistry jobRegistry, BookRepository bookRepository, EventLoopMonitor monitor) {
        this.jobRegistry = jobRegistry;
        this.bookRepository = bookRepository;
        this.monitor = monitor;
    }

    public void reindex(JsonObject command) {
        String jobId = command.getString("jobId");
        JsonObject trace = command.getJsonObject("trace");
        BookJob job = jobRegistry.require(jobId);

        try {
            List<Book> books = StreamSupport.stream(bookRepository.findAll().spliterator(), false).toList();
            job.markRunning(books.size());
            monitor.jobStarted(trace, job.toJson());

            int processed = 0;
            for (Book book : books) {
                simulateIndexing(book);
                processed++;
                job.markProgress(processed, "indexed book " + book.getId());
                monitor.jobProgress(trace, job.toJson());
            }

            job.markCompleted("reindex completed");
            monitor.jobCompleted(trace, job.toJson());
        } catch (RuntimeException e) {
            job.markFailed(e);
            monitor.jobFailed(trace, job.toJson(), e);
        }
    }

    private void simulateIndexing(Book book) {
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while indexing book " + book.getId(), e);
        }
    }
}
