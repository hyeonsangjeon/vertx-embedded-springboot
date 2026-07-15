package com.vertx.worker.mvc.service;

import com.vertx.worker.monitor.EventLoopMonitor;
import com.vertx.worker.mvc.dto.Book;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Implements the {@link BookAsyncService}, delegating calls to the transactional {@link BookServiceImpl}.
 *
 * @author hyeonsang jeon
 */
@Component
public class BookAsyncServiceImpl implements BookAsyncService {

    private final BookServiceImpl bookService;
    private final EventLoopMonitor monitor;

    public BookAsyncServiceImpl(BookServiceImpl bookService, EventLoopMonitor monitor) {
        this.bookService = bookService;
        this.monitor = monitor;
    }

    @Override
    public Future<JsonObject> save(JsonObject reqParam, JsonObject trace) {
        return complete(trace, "book.save", () -> bookService.save(reqParam));
    }

    @Override
    public Future<JsonObject> getAll(JsonObject trace) {
        return complete(trace, "book.list", bookService::getAll);
    }

    @Override
    public Future<JsonObject> get(Long bookId, JsonObject trace) {
        return complete(trace, "book.get", () -> bookService.get(bookId));
    }

    @Override
    public Future<JsonObject> update(JsonObject book, JsonObject trace) {
        return complete(trace, "book.update", () -> bookService.update(new Book(book)));
    }

    @Override
    public Future<JsonObject> delete(Long bookId, JsonObject trace) {
        return complete(trace, "book.delete", () -> bookService.delete(bookId));
    }

    private Future<JsonObject> complete(JsonObject trace, String step, Supplier<JsonObject> action) {
        monitor.workerStarted(trace, step);
        try {
            JsonObject result = action.get();
            monitor.workerCompleted(trace, step);
            return Future.succeededFuture(result);
        } catch (Exception e) {
            monitor.workerFailed(trace, step, e);
            return Future.failedFuture(e);
        }
    }
}
