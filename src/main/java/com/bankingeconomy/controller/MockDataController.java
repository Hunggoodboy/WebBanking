package com.bankingeconomy.controller;

import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/mock")
@RequiredArgsConstructor
public class MockDataController {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @PostMapping("/generate-transactions")
    public String generateTransactions() {
        try {
            // Lấy 2 tài khoản làm "diễn viên" để chuyển tiền qua lại
            // Chú ý: Bạn nhớ phải viết thêm hàm findByAccountNumber trong AccountRepository nhé
            Account acc1 = accountRepository.findByAccountNumber("ACC001")
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản ACC001"));
            Account acc2 = accountRepository.findByAccountNumber("ACC002")
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản ACC002"));

            List<Transaction> transactions = new ArrayList<>();

            // Chạy vòng lặp sinh 10.000 giao dịch
            for (int i = 1; i <= 10000; i++) {
                Transaction tx = new Transaction();

                // Random chiều chuyển tiền
                if (i % 2 == 0) {
                    tx.setFromAccount(acc1);
                    tx.setToAccount(acc2);
                } else {
                    tx.setFromAccount(acc2);
                    tx.setToAccount(acc1);
                }

                // Random số tiền từ 10k đến 5 triệu
                tx.setAmount(Math.round(Math.random() * 5000000 + 10000));
                tx.setDescription("Mock transaction chuyển khoản lần " + i);
                tx.setStatus("SUCCESS");

                // Random ngày giờ tạo (giả lập data của 30 ngày qua)
                tx.setCreatedAt(LocalDateTime.now().minusDays((int) (Math.random() * 30)));

                transactions.add(tx);
            }

            // Lưu 1 cục 10.000 dòng xuống DB
            transactionRepository.saveAll(transactions);
            return "✅ Đã tạo thành công 10,000 giao dịch giả!";

        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Lỗi khi sinh dữ liệu: " + e.getMessage();
        }
    }
}