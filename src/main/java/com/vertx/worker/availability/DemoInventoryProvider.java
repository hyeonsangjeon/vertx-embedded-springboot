package com.vertx.worker.availability;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

enum DemoInventoryProvider {
    SEOUL("seoul", 180, 3),
    BUSAN("busan", 320, 0),
    INCHEON("incheon", 240, 5);

    private final String slug;
    private final long latencyMs;
    private final int quantity;

    DemoInventoryProvider(String slug, long latencyMs, int quantity) {
        this.slug = slug;
        this.latencyMs = latencyMs;
        this.quantity = quantity;
    }

    String slug() {
        return slug;
    }

    long latencyMs() {
        return latencyMs;
    }

    int quantity() {
        return quantity;
    }

    static List<DemoInventoryProvider> all() {
        return List.of(values());
    }

    static List<String> slugs() {
        return Arrays.stream(values()).map(DemoInventoryProvider::slug).toList();
    }

    static Optional<DemoInventoryProvider> fromSlug(String slug) {
        return Arrays.stream(values()).filter(provider -> provider.slug.equals(slug)).findFirst();
    }
}
