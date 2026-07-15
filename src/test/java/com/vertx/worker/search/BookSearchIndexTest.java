package com.vertx.worker.search;

import com.vertx.worker.mvc.dto.Book;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookSearchIndexTest {

    private final BookSearchIndex index = new BookSearchIndex();

    @Test
    void searchesPublishedSnapshotCaseInsensitivelyAcrossTitleAndAuthor() {
        index.replace(List.of(
                index.createDocument(book(1, "Event Loop Patterns", "Hyeon-Sang Jeon")),
                index.createDocument(book(2, "Transactional Spring", "Another Author"))
        ));

        JsonArray matches = index.search("EVENT hyeon-sang");

        assertEquals(1, matches.size());
        assertEquals(1L, matches.getJsonObject(0).getLong("id"));
        assertEquals(2, index.size());
    }

    @Test
    void replacesTheWholeIndexInsteadOfExposingPartialUpdates() {
        index.replace(List.of(index.createDocument(book(1, "Old Index", "Author"))));
        index.replace(List.of(index.createDocument(book(2, "New Index", "Author"))));

        assertEquals(0, index.search("   ").size());
        assertEquals(0, index.search("old").size());
        assertEquals(1, index.search("new").size());
    }

    private Book book(long id, String name, String author) {
        return new Book(new JsonObject()
                .put("id", id)
                .put("name", name)
                .put("author", author)
                .put("pages", 100));
    }
}
