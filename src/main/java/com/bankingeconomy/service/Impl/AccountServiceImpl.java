package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.request.AccountRequest;
import com.bankingeconomy.dto.response.BalanceResponse;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.service.AccountService;
import com.bankingeconomy.service.BalanceCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final BalanceCacheService balanceCacheService;

    // ─────────────────────────────────────────
    // Xem số dư theo Account ID (Redis cache-first)
    // ─────────────────────────────────────────
    @Override
    public BalanceResponse getBalance(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        // Thử lấy từ Redis cache trước
        Double cachedBalance = balanceCacheService.getCachedBalance(accountId);

        if (cachedBalance != null) {
            log.info("Balance from CACHE for account {}: {}", account.getAccountNumber(), cachedBalance);
            return BalanceResponse.builder()
                    .accountNumber(account.getAccountNumber())
                    .balance(cachedBalance)
                    .source("CACHE")
                    .build();
        }

        // Cache miss → lấy từ DB rồi cache lại
        double dbBalance = account.getBalance();
        balanceCacheService.cacheBalance(accountId, dbBalance);

        log.info("Balance from DATABASE for account {}: {}", account.getAccountNumber(), dbBalance);
        return BalanceResponse.builder()
                .accountNumber(account.getAccountNumber())
                .balance(dbBalance)
                .source("DATABASE")
                .build();
    }

    // ─────────────────────────────────────────
    // Xem số dư theo số tài khoản
    // ─────────────────────────────────────────
    @Override
    public BalanceResponse getBalanceByAccountNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        return getBalance(account.getId());
    }

    // ─────────────────────────────────────────
    // Kiểm tra số dư đủ để chuyển tiền
    // ─────────────────────────────────────────
    @Override
    public boolean checkBalanceForTransfer(UUID accountId, double amount) {
        // Validate account tồn tại và ACTIVE
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        }

        // Kiểm tra balance qua Redis cache
        boolean hasEnough = balanceCacheService.hasEnoughBalance(accountId, amount);

        log.info("Balance check for account {} | amount={} | sufficient={}",
                account.getAccountNumber(), amount, hasEnough);

        return hasEnough;
    }
    @Override
    public ResponseData<?> createAccount(User user, AccountRequest request){
        if(accountRepository.findByAccountNumber(request.getAccountNumber()).isPresent()) {
            return ResponseData.builder()
                    .status(400)
                    .message("Tài khoản đã tồn tại")
                    .build();
        }
        Account account = Account.builder()
                .user(user)
                .accountNumber(request.getAccountNumber())
                .balance(0.0)
                .status(Account.AccountStatus.ACTIVE)
                .createdAt(new java.util.Date())
                .build();
        accountRepository.save(account);
        return ResponseData.builder()
                .status(201)
                .message("Account created successfully")
                .data(account.getId())
                .build();
    }
}
