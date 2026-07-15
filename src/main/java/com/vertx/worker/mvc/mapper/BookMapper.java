package com.vertx.worker.mvc.mapper;

import com.vertx.worker.mvc.dto.Book;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BookMapper {

    Book selectBookOne(Long id);
}
