package com.vertx.worker.mvc.repository;


import org.springframework.data.repository.CrudRepository;
import com.vertx.worker.mvc.dto.Book;


/**
 * A JPA Interface, this interface extends  {@link CrudRepository}  to dto {@link Book} entity.
 *
 * @author hyeonsang jeon
 */
public interface BookRepository extends CrudRepository<Book, Long> {
}