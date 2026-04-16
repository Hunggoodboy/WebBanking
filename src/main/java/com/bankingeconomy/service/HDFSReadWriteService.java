package com.bankingeconomy.service;

import com.bankingeconomy.entity.Transaction;

import java.io.IOException;
import java.util.List;

public interface HDFSReadWriteService {
    void writeTransactionsToHdfs(String month, List<Transaction> transactions) throws IOException;
    List<String> readTransactionsFromHdfs(String month) throws IOException ;
}
