package com.vertx.worker;

import com.vertx.worker.mvc.dto.Book;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VertxCodegenTest {

    @Test
    public void generatedServiceProxyClassesAreAvailable() throws Exception {
        Class.forName("com.vertx.worker.mvc.service.BookAsyncServiceVertxEBProxy");
        Class.forName("com.vertx.worker.mvc.service.BookAsyncServiceVertxProxyHandler");
    }

    @Test
    public void vertxJsonCodecIsCompatibleWithManagedJacksonVersion() {
        JsonArray data = new JsonArray()
                .add(new JsonObject()
                        .put("id", 1)
                        .put("name", "Vert.x"));

        assertEquals("Vert.x", data.getJsonObject(0).getString("name"));
        assertEquals(1, data.getJsonObject(0).getInteger("id").intValue());
    }

    @Test
    public void bookDataObjectRoundTripsThroughJsonObject() {
        Book book = new Book(new JsonObject()
                .put("id", 10)
                .put("name", "Modern Vert.x")
                .put("author", "Spring")
                .put("pages", 300));

        JsonObject json = book.toJson();

        assertEquals(10L, json.getLong("id"));
        assertEquals("Modern Vert.x", json.getString("name"));
        assertEquals("Spring", json.getString("author"));
        assertEquals(300, json.getInteger("pages"));
    }
}
