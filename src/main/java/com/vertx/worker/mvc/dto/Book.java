package com.vertx.worker.mvc.dto;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

//no working lombok, check here.
@Entity
@DataObject(generateConverter = true)
public class Book {
    private static final long serialVersionUID = 8203836758273948712L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name = "";
    private String author = "";
    private int pages = 0;


    // Mandatory for JPA entities
    protected Book() {
    }

    // Mandatory for data objects DTO parsing
    public Book(JsonObject jsonObject) {
        this.id = jsonObject.getLong("id");
        this.name = jsonObject.getString("name", "");
        this.author = jsonObject.getString("author", "");
        this.pages = jsonObject.getInteger("pages", 0);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject()
                .put("name", name)
                .put("author", author)
                .put("pages", pages);
        if (id != null) {
            json.put("id", id);
        }
        return json;
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    @Override
    public String toString() {
        return "Book{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", author='" + author + '\'' +
                ", pages=" + pages +
                '}';
    }
}
