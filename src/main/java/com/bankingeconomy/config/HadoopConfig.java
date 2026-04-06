package com.bankingeconomy.config;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.net.URI;

@org.springframework.context.annotation.Configuration
public class HadoopConfig {

    @Bean
    public FileSystem fileSystem() throws IOException {
        System.setProperty("HADOOP_USER_NAME", "root");

        Configuration config = new Configuration();
        config.set("fs.defaultFS", "hdfs://localhost:9000");
        config.setBoolean("dfs.client.use.datanode.hostname", true);
        config.setInt("dfs.replication", 1);
        config.set("dfs.client.socket-timeout", "60000");

        return FileSystem.newInstance(URI.create("hdfs://localhost:9000"), config);
    }
}