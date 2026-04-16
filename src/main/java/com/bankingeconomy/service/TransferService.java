package com.bankingeconomy.service;

import com.bankingeconomy.dto.request.TransferRequest;
import com.bankingeconomy.dto.response.TransferResponse;
import com.bankingeconomy.entity.User;

public interface TransferService {
    TransferResponse initiateTransfer(User currentUser, TransferRequest request);
}