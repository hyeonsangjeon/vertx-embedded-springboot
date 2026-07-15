package com.vertx.worker.mvc.service;

import com.vertx.worker.mvc.dao.BookDao;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vertx.worker.mvc.dto.Book;
import com.vertx.worker.mvc.repository.BookRepository;


/**
 * Blocking transactional service. Calls reach this class only through a worker verticle.
 *
 * @author hyeonsang jeon
 */
@Service
public class BookServiceImpl {

    private final BookDao bookDao;
    private final BookRepository bookRepository;

    public BookServiceImpl(BookDao bookDao, BookRepository bookRepository) {
        this.bookDao = bookDao;
        this.bookRepository = bookRepository;
    }

    @Transactional
    public JsonObject save(JsonObject reqParam) {
        JsonObject result = new JsonObject();
        Book data = bookRepository.save(new Book(reqParam));

        result.put("statusCode", HttpStatus.CREATED.value());
        result.put("data", data.toJson());
        result.put("message", "book created");
        return result;
    }

    @Transactional(readOnly = true)
    public JsonObject getAll() {
        JsonObject result = new JsonObject();
        JsonArray jsonArray = new JsonArray();
        bookRepository.findAll().forEach(book -> jsonArray.add(book.toJson()));

        result.put("statusCode", HttpStatus.OK.value());
        result.put("data", jsonArray);
        result.put("message", "book list");
        return result;
    }


    @Transactional(readOnly = true)
    public JsonObject get(Long bookId) {
        JsonObject result = new JsonObject();

        // MyBatis read path complements the Spring Data JPA write path in this sample.
        Book book = bookDao.selectBookOne(bookId);

        if (book != null) {
            result.put("statusCode", HttpStatus.OK.value());
            result.put("data", book.toJson());
            result.put("message", "book found");
        } else {
            result.put("statusCode", HttpStatus.NOT_FOUND.value());
            result.put("data", new JsonObject());
            result.put("message", "book not found");
        }

        return result;
    }

    @Transactional
    public JsonObject update(Book chkBook) {
        JsonObject result = new JsonObject();

        bookRepository.save(chkBook);

        Book changedBook = bookRepository.findById(chkBook.getId()).orElseThrow(
                () -> new IllegalStateException("Book not found after update: " + chkBook.getId())
        );

        result.put("statusCode", HttpStatus.OK.value());
        result.put("data", changedBook.toJson());
        result.put("message", "book updated");
        return result;
    }

    @Transactional
    public JsonObject delete(Long bookId) {
        JsonObject result = new JsonObject();

        bookRepository.deleteById(bookId);

        result.put("statusCode", HttpStatus.OK.value());
        result.put("message", "book deleted");
        return result;

    }

}
