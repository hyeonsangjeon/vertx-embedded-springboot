package com.vertx.worker.mvc.service;

import com.vertx.worker.mvc.dto.Book;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * Let's see <a href="http://vertx.io/docs/vertx-service-proxy/java/">Vert.x Service Proxies</a>.
 *
 * @author hyeonsang jeon
 */
@ProxyGen
public interface BookAsyncService {
    String ADDRESS = BookAsyncService.class.getName();

    void save(JsonObject reqParam, Handler<AsyncResult<JsonObject>> resultHandler);

    void getAll(Handler<AsyncResult<JsonObject>> resultHandler);

    void get(Long bookId, Handler<AsyncResult<JsonObject>> resultHandler);

    void update(Book reqParam, Handler<AsyncResult<JsonObject>> resultHandler);

    void delete(Long bookId, Handler<AsyncResult<JsonObject>> resultHandler);
}
