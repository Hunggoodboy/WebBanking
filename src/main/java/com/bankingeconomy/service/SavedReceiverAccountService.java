package com.bankingeconomy.service;

import com.bankingeconomy.dto.request.SavedReceiverAccountRequest;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.dto.response.SavedReceiverAccountResponse;
import com.bankingeconomy.entity.User;

import java.util.List;

public interface SavedReceiverAccountService {
    List<SavedReceiverAccountResponse> getMySavedReceivers(User user);

    ResponseData<SavedReceiverAccountResponse> saveReceiver(User user, SavedReceiverAccountRequest request);
}
