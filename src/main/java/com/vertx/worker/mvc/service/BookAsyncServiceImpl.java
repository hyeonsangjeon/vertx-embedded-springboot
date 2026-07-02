package com.vertx.worker.mvc.service;

import com.vertx.worker.monitor.EventLoopMonitor;
import io.vertx.core.json.JsonObject;
import org.springframework.stereotype.Component;
import com.vertx.worker.mvc.dto.Book;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

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
    public void save(JsonObject reqParam, JsonObject trace, Handler<AsyncResult<JsonObject>> resultHandler) {
        complete(trace, "book.save", resultHandler, () -> bookService.save(reqParam));
    }

    @Override
    public void getAll(JsonObject trace, Handler<AsyncResult<JsonObject>> resultHandler) {
        complete(trace, "book.list", resultHandler, bookService::getAll);
    }

    @Override
    public void get(Long bookId, JsonObject trace, Handler<AsyncResult<JsonObject>> resultHandler) {
        complete(trace, "book.get", resultHandler, () -> bookService.get(bookId));
    }

    @Override
    public void update(Book book, JsonObject trace, Handler<AsyncResult<JsonObject>> resultHandler) {
        complete(trace, "book.update", resultHandler, () -> bookService.update(book));
    }

    @Override
    public void delete(Long bookId, JsonObject trace, Handler<AsyncResult<JsonObject>> resultHandler) {
        complete(trace, "book.delete", resultHandler, () -> bookService.delete(bookId));
    }

    private void complete(JsonObject trace, String step, Handler<AsyncResult<JsonObject>> resultHandler,
                          Supplier<JsonObject> action) {
        monitor.workerStarted(trace, step);
        try {
            JsonObject result = action.get();
            monitor.workerCompleted(trace, step);
            resultHandler.handle(Future.succeededFuture(result));
        } catch (Exception e) {
            monitor.workerFailed(trace, step, e);
            resultHandler.handle(Future.failedFuture(e));
        }
    }
}
