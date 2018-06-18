package com.vertx.worker.mvc.dao;

import com.vertx.worker.mvc.dto.Book;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

@Repository("BookDao")
public class BookDao {
    private static final Logger logger = LoggerFactory.getLogger(BookDao.class);

    @Autowired
    @Qualifier("BookSqlSessionTemplate")
    private SqlSession sqlSession;

    public String getCurrentTime() throws DataAccessException {
        return sqlSession.selectOne("example.mappers.Book.getCurrentTime");
    }

    public Book selectBookOne(Long id) throws DataAccessException {
        Book result = sqlSession.selectOne("example.mappers.Book.selectBookOne",id);
        return result;
    }


}
