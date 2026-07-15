package com.vertx.worker.mvc.repository;

import com.vertx.worker.mvc.dto.Book;
import org.springframework.data.repository.CrudRepository;

/** Spring Data write path for {@link Book} entities. */
public interface BookRepository extends CrudRepository<Book, Long> {
}
