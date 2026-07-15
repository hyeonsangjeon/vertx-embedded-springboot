package com.vertx.worker.search;

import com.vertx.worker.mvc.dto.Book;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Small immutable-snapshot search index used to make the background-job result observable.
 * A real platform would publish these documents to Elasticsearch, OpenSearch, or a vector store.
 */
@Component
public class BookSearchIndex {
    private final AtomicReference<List<IndexedBook>> snapshot = new AtomicReference<>(List.of());

    public IndexedBook createDocument(Book book) {
        String text = normalize(book.getName()) + " " + normalize(book.getAuthor());
        return new IndexedBook(book.getId(), book.getName(), book.getAuthor(), book.getPages(), text);
    }

    public void replace(List<IndexedBook> documents) {
        snapshot.set(List.copyOf(documents));
    }

    public JsonArray search(String query) {
        List<String> terms = Arrays.stream(normalize(query).split("\\s+"))
                .filter(term -> !term.isBlank())
                .toList();
        if (terms.isEmpty()) {
            return new JsonArray();
        }

        JsonArray results = new JsonArray();
        snapshot.get().stream()
                .filter(document -> terms.stream().allMatch(document.searchableText()::contains))
                .map(IndexedBook::toJson)
                .forEach(results::add);
        return results;
    }

    public int size() {
        return snapshot.get().size();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    public record IndexedBook(Long id, String name, String author, int pages, String searchableText) {
        public JsonObject toJson() {
            return new JsonObject()
                    .put("id", id)
                    .put("name", name)
                    .put("author", author)
                    .put("pages", pages);
        }
    }
}
