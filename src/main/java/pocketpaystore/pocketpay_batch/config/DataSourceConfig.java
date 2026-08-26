package pocketpaystore.pocketpay_batch.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@MapperScan(basePackages = "pocketpaystore.pocketpay_batch.mapper.business",
        sqlSessionFactoryRef = "businessSqlSessionFactory")
@MapperScan(basePackages = "pocketpaystore.pocketpay_batch.mapper.batch",
        sqlSessionFactoryRef = "batchSqlSessionFactory")
public class DataSourceConfig {

    @Bean(name = "businessDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.business")
    public DataSource businessDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    @Primary
    @Bean(name = "batchDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.batch")
    public DataSource batchDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "businessSqlSessionFactory")
    public SqlSessionFactory businessSqlSessionFactory(@Qualifier("businessDataSource") DataSource dataSource)
            throws Exception {
        // classpath*: (단일 classpath:가 아니라) — 아직 매퍼 XML이 하나도 없는 지금 상태에서
        // 단일 classpath:는 "경로 자체가 존재해야 함"을 요구해서 FileNotFoundException이 난다.
        // classpath*:는 없으면 그냥 빈 결과로 넘어간다(MyBatis-Spring 공식 예제에서도 이 표기를 씀).
        return buildSqlSessionFactory(dataSource, "classpath*:mapper/business/**/*.xml");
    }

    @Bean(name = "businessSqlSessionTemplate")
    public SqlSessionTemplate businessSqlSessionTemplate(
            @Qualifier("businessSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "businessTransactionManager")
    public PlatformTransactionManager businessTransactionManager(
            @Qualifier("businessDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Primary
    @Bean(name = "batchSqlSessionFactory")
    public SqlSessionFactory batchSqlSessionFactory(@Qualifier("batchDataSource") DataSource dataSource)
            throws Exception {
        return buildSqlSessionFactory(dataSource, "classpath*:mapper/batch/**/*.xml");
    }

    @Primary
    @Bean(name = "batchSqlSessionTemplate")
    public SqlSessionTemplate batchSqlSessionTemplate(
            @Qualifier("batchSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Primary
    @Bean(name = "batchTransactionManager")
    public PlatformTransactionManager batchTransactionManager(
            @Qualifier("batchDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    private SqlSessionFactory buildSqlSessionFactory(DataSource dataSource, String mapperLocationPattern)
            throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(mapperLocationPattern));
        org.apache.ibatis.session.Configuration mybatisConfiguration = new org.apache.ibatis.session.Configuration();
        mybatisConfiguration.setMapUnderscoreToCamelCase(true); // DB는 snake_case, 자바 필드는 camelCase
        factoryBean.setConfiguration(mybatisConfiguration);
        return factoryBean.getObject();
    }
}
