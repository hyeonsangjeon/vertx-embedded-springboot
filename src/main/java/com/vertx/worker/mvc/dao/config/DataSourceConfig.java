/*************************************************
 * File Name : API 용 DataSource
 * Author:     hyeonsangjeon
 * Crt Date :  2017.07.10
 * DESC : API mybatis config, 복잡한 쿼리나 서브쿼리의 경우 가독성을 위해 mybatis를 이용한다. 
 ***************Update Record*********************
 * Date             Author                 UpdateInfo
 * 2017.07.03       hyeonsangjeon                Make file.
 *
 */
package com.vertx.worker.mvc.dao.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    public SqlSessionFactory bookSqlSessionFactory(DataSource firstDataSource, ApplicationContext applicationContext) throws Exception {
        SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(firstDataSource);
        sqlSessionFactoryBean.setMapperLocations(applicationContext.getResources("classpath:mappers/book/*.xml"));
        return sqlSessionFactoryBean.getObject();
    }

    @Bean
    public SqlSessionTemplate BookSqlSessionTemplate(SqlSessionFactory bookSqlSessionFactory) throws Exception {
        return new SqlSessionTemplate(bookSqlSessionFactory);
    }
}