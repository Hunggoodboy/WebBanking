package com.bankingeconomy.controller;

import com.bankingeconomy.dto.request.AccountRequest;
import com.bankingeconomy.dto.response.BalanceResponse;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/create")
    public ResponseData<?> createAccount(@AuthenticationPrincipal User user, AccountRequest request){
        return accountService.createAccount(user, request);
    }

    // ─────────────────────────────────────────
    // GET /api/accounts/{id}/balance — Xem số dư (Redis cache)
    // ─────────────────────────────────────────
    @GetMapping("/{id}/balance")
    public ResponseData<BalanceResponse> getBalance(@PathVariable UUID id) {
        BalanceResponse result = accountService.getBalance(id);
        return new ResponseData<>(HttpStatus.OK.value(), "Thành công", result);
    }

    // ─────────────────────────────────────────
        // GET /api/accounts/number/{accountNumber}/balance — Xem số dư theo số tài khoản
    // ─────────────────────────────────────────
    @GetMapping("/number/{accountNumber}/balance")
    public ResponseData<BalanceResponse> getBalanceByAccountNumber(@PathVariable String accountNumber) {
        BalanceResponse result = accountService.getBalanceByAccountNumber(accountNumber);
        return new ResponseData<>(HttpStatus.OK.value(), "Thành công", result);
    }

    // ─────────────────────────────────────────
    // GET /api/accounts/{id}/check-balance?amount=xxx — Kiểm tra đủ số dư để chuyển tiền
    // ─────────────────────────────────────────
    @GetMapping("/{id}/check-balance")
    public ResponseData<Boolean> checkBalance(@PathVariable UUID id, @RequestParam double amount) {
        boolean hasEnough = accountService.checkBalanceForTransfer(id, amount);
        String message = hasEnough ? "Số dư đủ để thực hiện giao dịch" : "Số dư không đủ";
        return new ResponseData<>(HttpStatus.OK.value(), message, hasEnough);
    }
}
