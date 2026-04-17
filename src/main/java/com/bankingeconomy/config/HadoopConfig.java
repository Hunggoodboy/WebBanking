package com.bankingeconomy.config;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.net.URI;

@org.springframework.context.annotation.Configuration
public class HadoopConfig {

    @Bean
    public Configuration configuration() {
        System.setProperty("HADOOP_USER_NAME", "root");
        Configuration config = new Configuration();
        config.set("fs.defaultFS", "hdfs://localhost:9000");
        config.setBoolean("dfs.client.use.datanode.hostname", true);
        config.setInt("dfs.replication", 1);
        // Quan trọng: Để MapReduce chạy được trên môi trường local/UTM của bạn
        config.set("mapreduce.framework.name", "local");
        return config;
    }

    @Bean
    public FileSystem fileSystem(Configuration configuration) throws IOException {
        return FileSystem.get(URI.create("hdfs://localhost:9000"), configuration);
    }
}