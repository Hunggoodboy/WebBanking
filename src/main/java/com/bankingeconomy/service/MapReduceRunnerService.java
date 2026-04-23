package com.bankingeconomy.service;

public interface MapReduceRunnerService {
    String runTransactionTotalJob(String month);
    String runTopTransferTimeJob(String month);
}
