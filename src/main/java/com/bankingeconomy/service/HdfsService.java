package com.bankingeconomy.service;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class HdfsService {

    @Autowired
    private FileSystem fileSystem;

    // Hàm tạo thư mục trên HDFS
    public void createDirectory(String dirPath) throws Exception {
        Path path = new Path(dirPath);
        if (!fileSystem.exists(path)) {
            fileSystem.mkdirs(path);
        }
    }

    // Hàm upload file từ máy tính lên HDFS
    public void uploadFile(String localFilePath, String hdfsDirPath) throws Exception {
        Path localPath = new Path(localFilePath);
        Path hdfsPath = new Path(hdfsDirPath);
        fileSystem.copyFromLocalFile(localPath, hdfsPath);
    }
}