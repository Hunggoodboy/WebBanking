//package com.bankingeconomy.controller;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.math.BigDecimal;
//import java.util.UUID;
//
//@RestController
//@RequestMapping("/account")
//@RequiredArgsConstructor
//public class AccountController {
//
//    private final AccountService accountService;
//
//    // Xem số dư — đọc Redis trước
//    @GetMapping("/{accountId}/balance")
//    public ResponseEntity<BigDecimal> getBalance(@PathVariable UUID accountId) {
//        return ResponseEntity.ok(accountService.getBalance(accountId));
//    }
//
//    // Lịch sử giao dịch của tài khoản
//    @GetMapping("/{accountId}/transactions")
//    public ResponseEntity<List<TransactionDTO>> getHistory(@PathVariable UUID accountId) {
//        return ResponseEntity.ok(accountService.getTransactionHistory(accountId));
//    }
//}
