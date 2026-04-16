package com.bankingeconomy.service;

import com.bankingeconomy.dto.request.TransferRequest;
import com.bankingeconomy.dto.response.TransferResponse;

public interface TransferService {
    TransferResponse initiateTransfer(TransferRequest request);
}