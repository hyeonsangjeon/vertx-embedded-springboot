package com.vertx.worker.mvc.service;

import io.vertx.core.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.vertx.worker.mvc.dto.Book;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * Implements the {@link BookAsyncService}, delegating calls to the transactional {@link BookServiceImpl}.
 * @author hyeonsang jeon
 */
@Component
public class BookAsyncServiceImpl implements BookAsyncService{
	
    @Autowired
	BookServiceImpl bookServiceImpl;
	
    @Override
	public void save(JsonObject reqParam, Handler<AsyncResult<JsonObject>> resultHandler) {
            JsonObject res = bookServiceImpl.save(reqParam);
    		Future.succeededFuture(res).setHandler(resultHandler);
    }
    
    @Override
    public void getAll(Handler<AsyncResult<JsonObject>> resultHandler) {
    		JsonObject res = bookServiceImpl.getAll();
    		Future.succeededFuture(res).setHandler(resultHandler);
    }
	 
    @Override
    public void get(Long bookId, Handler<AsyncResult<JsonObject>> resultHandler) {

            JsonObject res = bookServiceImpl.get(bookId);
    		Future.succeededFuture(res).setHandler(resultHandler);
    }
	 
    @Override
    public void update(Book book, Handler<AsyncResult<JsonObject>> resultHandler) {
            JsonObject res = bookServiceImpl.update(book);
    		Future.succeededFuture(res).setHandler(resultHandler);
    }
	 
    @Override
    public void delete(Long bookId, Handler<AsyncResult<JsonObject>> resultHandler) {

            JsonObject del = bookServiceImpl.delete(bookId);
    		Future.succeededFuture(del).setHandler(resultHandler);
    }


}