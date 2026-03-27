package com.bankingeconomy.config;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.net.URI;

@org.springframework.context.annotation.Configuration
public class HadoopConfig {

    @Bean
    public FileSystem fileSystem() throws Exception {
        // 1. Khởi tạo cấu hình Hadoop
        Configuration conf = new Configuration();
        
        // 2. Chỉ định địa chỉ NameNode (port 9000 từ Docker)
        String hdfsUri = "hdfs://localhost:9000";
        
        // 3. Trả về đối tượng FileSystem với quyền user là "root"
        return FileSystem.get(URI.create(hdfsUri), conf, "root");
    }
}