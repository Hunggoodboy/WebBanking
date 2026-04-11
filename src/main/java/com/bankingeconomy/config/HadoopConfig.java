package com.bankingeconomy.config;

import java.net.URI;

import org.apache.hadoop.fs.FileSystem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HadoopConfig {

    @Bean
    @ConditionalOnProperty(name = "hadoop.enabled", havingValue = "true") 
    public FileSystem fileSystem() throws Exception {
        org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        String hdfsUri = "hdfs://localhost:9000";
        return FileSystem.get(URI.create(hdfsUri), conf, "root");
    }
}