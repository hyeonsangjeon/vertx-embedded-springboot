package com.vertx.worker.mvc.mapper;

import com.vertx.worker.mvc.dto.Book;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BookMapper {

    String getCurrentTime();

    Book selectBookOne(Long id);
}
