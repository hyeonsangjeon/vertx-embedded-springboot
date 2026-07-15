package com.vertx.worker.job;

import com.vertx.worker.monitor.EventLoopMonitor;
import com.vertx.worker.mvc.dto.Book;
import com.vertx.worker.mvc.repository.BookRepository;
import com.vertx.worker.search.BookSearchIndex;
import io.vertx.core.json.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    private final BookSearchIndex searchIndex;
    private final long itemDelayMs;

    public BookReindexJobWorker(
            BookJobRegistry jobRegistry,
            BookRepository bookRepository,
            EventLoopMonitor monitor,
            BookSearchIndex searchIndex,
            @Value("${demo.reindex.item-delay-ms:150}") long itemDelayMs) {
        this.jobRegistry = jobRegistry;
        this.bookRepository = bookRepository;
        this.monitor = monitor;
        this.searchIndex = searchIndex;
        this.itemDelayMs = Math.max(itemDelayMs, 0);
    }

    public void reindex(JsonObject command) {
        String jobId = command.getString("jobId");
        JsonObject trace = command.getJsonObject("trace");
        BookJob job = jobRegistry.require(jobId);

        try {
            List<Book> books = StreamSupport.stream(bookRepository.findAll().spliterator(), false).toList();
            job.markRunning(books.size());
            monitor.jobStarted(trace, job.toJson());

            List<BookSearchIndex.IndexedBook> documents = new ArrayList<>(books.size());
            int processed = 0;
            for (Book book : books) {
                BookSearchIndex.IndexedBook document = searchIndex.createDocument(book);
                simulateBlockingIndexWrite(book);
                documents.add(document);
                processed++;
                job.markProgress(processed, "indexed book " + book.getId());
                monitor.jobProgress(trace, job.toJson());
            }

            searchIndex.replace(documents);
            job.markCompleted(
                    "search index published",
                    new JsonObject()
                            .put("indexedDocuments", documents.size())
                            .put("searchExample", "/book/search?q=Hyeon-Sang")
            );
            monitor.jobCompleted(trace, job.toJson());
        } catch (RuntimeException e) {
            job.markFailed(e);
            monitor.jobFailed(trace, job.toJson(), e);
        }
    }

    private void simulateBlockingIndexWrite(Book book) {
        try {
            Thread.sleep(itemDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while indexing book " + book.getId(), e);
        }
    }
}
