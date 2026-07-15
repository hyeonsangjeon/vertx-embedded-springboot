package com.vertx.worker.mvc.dao;

import com.vertx.worker.mvc.dto.Book;
import com.vertx.worker.mvc.mapper.BookMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository
public class BookDao {

    private final BookMapper bookMapper;

    public BookDao(BookMapper bookMapper) {
        this.bookMapper = bookMapper;
    }

    public Book selectBookOne(Long id) throws DataAccessException {
        return bookMapper.selectBookOne(id);
    }
}
