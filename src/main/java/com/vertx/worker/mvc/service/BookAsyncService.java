package com.vertx.worker.mvc.service;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Future-based service contract transported over the Vert.x event bus.
 *
 * @author hyeonsang jeon
 */
@ProxyGen
public interface BookAsyncService {
    String ADDRESS = BookAsyncService.class.getName();

    Future<JsonObject> save(JsonObject reqParam, JsonObject trace);

    Future<JsonObject> getAll(JsonObject trace);

    Future<JsonObject> get(Long bookId, JsonObject trace);

    Future<JsonObject> update(JsonObject reqParam, JsonObject trace);

    Future<JsonObject> delete(Long bookId, JsonObject trace);
}
