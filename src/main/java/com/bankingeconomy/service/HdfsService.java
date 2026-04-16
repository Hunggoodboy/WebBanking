package com.bankingeconomy.service;

public interface HdfsService {
    public void createDirectory(String dirPath) throws Exception;
    public void uploadFile(String fileName, String filePath) throws Exception;
}
