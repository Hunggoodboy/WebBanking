package com.bankingeconomy.service;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
//@ConditionalOnProperty(name = "hadoop.enabled", havingValue = "true")
public class HdfsService {

    @Autowired
    private FileSystem fileSystem;

    public void createDirectory(String dirPath) throws Exception {
        Path path = new Path(dirPath);
        if (!fileSystem.exists(path)) {
            fileSystem.mkdirs(path);
        }
    }

    public void uploadFile(String localFilePath, String hdfsDirPath) throws Exception {
        Path localPath = new Path(localFilePath);
        Path hdfsPath = new Path(hdfsDirPath);
        fileSystem.copyFromLocalFile(localPath, hdfsPath);
    }
}