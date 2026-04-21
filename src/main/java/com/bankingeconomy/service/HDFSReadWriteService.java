package com.bankingeconomy.service;


import com.bankingeconomy.dto.HdfsTransactionDTO;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.entity.Transaction;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface HDFSReadWriteService {
    void writeTransactionsToHDFS(Map<String, List<String> > partitionData) throws IOException;
    List<String> readPartition(String province, String district, int year, String quarter) throws IOException;
    ResponseData<?> excuteWriteToHdfs();
    List<String> readByProvinceAndQuarter(String province, int year, String quarter) throws IOException;
    boolean partitionExists(String province, String district, int year, String quarter) throws IOException;
    void deletePartition(String province, String district, int year, String quarter) throws IOException;
    List<String> listAllPartitions() throws IOException;
    void clearAllTransactionData();
}