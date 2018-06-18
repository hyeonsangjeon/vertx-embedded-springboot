package com.vertx.worker.mvc.service;

import static java.util.stream.Collectors.toList;


import java.util.List;

import java.util.stream.StreamSupport;

import com.google.gson.Gson;
import com.vertx.worker.mvc.dao.BookDao;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.vertx.worker.mvc.dto.Book;
import com.vertx.worker.mvc.repository.BookRepository;


/**
 * Concrete class, this class implement  based small bussiness logic needed with sysynchronous
 *
 * @author hyeonsang jeon
 */
@Service
@Transactional
public class BookServiceImpl {

    @Autowired
    BookDao bookDao;

    @Autowired
    BookRepository bookRepository;

    public JsonObject save(JsonObject reqParam) {
        JsonObject result = new JsonObject();
        Book data = bookRepository.save(new Book(reqParam));

        result.put("statusCode", HttpStatus.OK.value());
        result.put("data", data.toJson());
        result.put("message", "book save success");
        return result;
    }

    public JsonObject getAll() {
        JsonObject result = new JsonObject();

        Iterable<Book> all = bookRepository.findAll();
        List<Book> res = StreamSupport.stream(all.spliterator(), false).collect(toList());
        Gson gson = new Gson();
        JsonArray jsonArray = new JsonArray(gson.toJson(res));

        result.put("statusCode", HttpStatus.OK.value());
        result.put("data", jsonArray);
        result.put("message", "book list information");
        return result;
    }


    public JsonObject get(Long bookId) {
        JsonObject result = new JsonObject();
        //JPA VERSION
        //Book book = bookRepository.findOne(bookId);

        // Mybatis
        Book book =bookDao.selectBookOne(bookId);

        if (book != null) {
            result.put("statusCode", HttpStatus.OK.value());
            result.put("data", book.toJson());
            result.put("message", "unique book information");
        } else {
            result.put("statusCode", HttpStatus.NOT_FOUND.value());
            result.put("data", new JsonObject());
            result.put("message", "no search book information");
        }

        return result;
    }

    public JsonObject update(Book chkBook) {
        JsonObject result = new JsonObject();

        bookRepository.save(chkBook);

        Book changedBook = bookRepository.findOne(chkBook.getId());

        result.put("statusCode", HttpStatus.OK.value());
        result.put("data", changedBook.toJson());
        result.put("message", "update book success");
        return result;
    }

    public JsonObject delete(Long bookId) {
        JsonObject result = new JsonObject();

        bookRepository.delete(bookId);

        result.put("statusCode", HttpStatus.OK.value());
        result.put("message", "delete book success");
        return result;

    }

}
