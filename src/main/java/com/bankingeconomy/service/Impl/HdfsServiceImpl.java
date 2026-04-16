package com.bankingeconomy.service.Impl;

import com.bankingeconomy.service.HdfsService;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Hdfs;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class HdfsServiceImpl implements HdfsService {

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