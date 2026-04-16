package com.bankingeconomy.service;

import com.bankingeconomy.dto.request.AccountRequest;
import com.bankingeconomy.dto.response.BalanceResponse;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.entity.User;

import java.util.UUID;

public interface AccountService {

    /**
     * Xem số dư tài khoản — ưu tiên lấy từ Redis cache, fallback DB
     */
    BalanceResponse getBalance(UUID accountId);

    /**
     * Xem số dư theo số tài khoản
     */
    BalanceResponse getBalanceByAccountNumber(String accountNumber);

    /**
     * Kiểm tra số dư có đủ để chuyển tiền không
     *
     * @return true nếu đủ, false nếu không đủ
     */
    boolean checkBalanceForTransfer(UUID accountId, double amount);

    ResponseData<?> createAccount(User user, AccountRequest request);
}
