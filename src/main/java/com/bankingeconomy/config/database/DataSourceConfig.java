package com.bankingeconomy.config.database;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Bean(name = "centralDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.central")
    public DataSource centralDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "northDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.north")
    public DataSource northDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "midDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.mid")
    public DataSource midDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "southDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.south")
    public DataSource southDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @Primary
    public DataSource dataSource() {
        DynamicDataSource dynamicDataSource = new DynamicDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DbType.CENTRAL, centralDataSource());
        targetDataSources.put(DbType.NORTH, northDataSource());
        targetDataSources.put(DbType.MID, midDataSource());
        targetDataSources.put(DbType.SOUTH, southDataSource());

        dynamicDataSource.setTargetDataSources(targetDataSources);

        // ĐÂY LÀ CHỖ QUAN TRỌNG NHẤT: Set BankingEconomy làm mặc định!
        dynamicDataSource.setDefaultTargetDataSource(centralDataSource());

        return dynamicDataSource;
    }
}