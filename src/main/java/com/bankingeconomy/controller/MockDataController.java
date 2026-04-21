package com.bankingeconomy.controller;

import com.bankingeconomy.dto.event.TransferEvent;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@RestController
@RequestMapping("/api/mock")
@RequiredArgsConstructor
@EnableScheduling
public class MockDataController {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private boolean isSimulating = false;

    @PostMapping("/start")
    public String startSimulation() {
        isSimulating = true;
        return "✅ Đã BẬT giả lập: Mỗi phút sẽ tự động sinh ~100 giao dịch bắn vào Kafka!";
    }

    @PostMapping("/stop")
    public String stopSimulation() {
        isSimulating = false;
        return "🛑 Đã TẮT giả lập sinh giao dịch.";
    }

    @Scheduled(fixedRate = 60000)
    public void generateTransactionsPerMinute() {
        if (!isSimulating) return;

        try {
            // Lấy 2 tài khoản ngẫu nhiên từ DB (SQL Server dùng NEWID())
            List<Account> randomAccounts = accountRepository.findTwoRandomAccounts();

            if (randomAccounts.size() < 2) {
                log.error("❌ Không đủ tài khoản trong DB để giả lập (cần ít nhất 2).");
                return;
            }

            Account acc1 = randomAccounts.get(0);
            Account acc2 = randomAccounts.get(1);

            int txCount = ThreadLocalRandom.current().nextInt(80, 121);

            for (int i = 0; i < txCount; i++) {
                boolean direction = i % 2 == 0;
                Account fromAcc = direction ? acc1 : acc2;
                Account toAcc   = direction ? acc2 : acc1;

                long amount = ThreadLocalRandom.current().nextLong(10_000, 5_000_000);

                Transaction pendingTx = new Transaction();
                pendingTx.setFromAccount(fromAcc);
                pendingTx.setToAccount(toAcc);
                pendingTx.setAmount((double) amount);
                pendingTx.setDescription("Mock Auto - " + LocalDateTime.now());
                pendingTx.setStatus("PENDING");
                pendingTx.setCreatedAt(LocalDateTime.now());

                Transaction savedTx = transactionRepository.save(pendingTx);

                TransferEvent event = TransferEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .transactionId(savedTx.getId().toString())
                        .fromAccountId(fromAcc.getId().toString())
                        .fromAccountNumber(fromAcc.getAccountNumber())
                        .senderUserId(fromAcc.getUser() != null
                                ? fromAcc.getUser().getId().toString()
                                : UUID.randomUUID().toString())
                        .toAccountId(toAcc.getId().toString())
                        .toAccountNumber(toAcc.getAccountNumber())
                        .receiverUserId(toAcc.getUser() != null
                                ? toAcc.getUser().getId().toString()
                                : UUID.randomUUID().toString())
                        .amount(BigDecimal.valueOf(amount))
                        .description(pendingTx.getDescription())
                        .status(TransferEvent.TransferStatus.PENDING)
                        .timestamp(Instant.now())
                        .fromProvince(fromAcc.getUser().getProvince())
                        .fromDistrict(fromAcc.getUser().getDistrict())
                        .toProvince(toAcc.getUser().getProvince())
                        .toDistrict(toAcc.getUser().getDistrict())
                        .build();

                kafkaTemplate.send("transfer-topic", savedTx.getId().toString(), event);
            }

            log.info("🚀 [MOCK] Đã sinh và bắn {} giao dịch vào Kafka (acc: {} → {}).",
                    txCount, acc1.getAccountNumber(), acc2.getAccountNumber());

        } catch (Exception e) {
            log.error("❌ Lỗi khi sinh dữ liệu tự động: ", e);
        }
    }
}