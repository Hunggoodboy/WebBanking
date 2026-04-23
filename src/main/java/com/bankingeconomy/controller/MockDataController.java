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
import org.springframework.web.bind.annotation.RequestParam;
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

    private static final long MIN_TRANSFER_AMOUNT = 10_000L;
    private static final long MAX_TRANSFER_AMOUNT = 1_000_000L;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private volatile boolean isSimulating = false;

    @PostMapping("/start")
    public String startSimulation() {
        isSimulating = true;
        return "Da bat gia lap. Moi phut he thong se tao xap xi 100 giao dich.";
    }

    @PostMapping("/stop")
    public String stopSimulation() {
        isSimulating = false;
        return "Da tat gia lap sinh giao dich.";
    }

    @PostMapping("/run-once")
    public String runOnce(@RequestParam(defaultValue = "100") int count) {
        int generated = generateTransactions(Math.max(1, count));
        return "Da tao " + generated + " giao dich mock ngay lap tuc.";
    }

    @Scheduled(fixedRate = 60000)
    public void generateTransactionsPerMinute() {
        if (!isSimulating) {
            return;
        }

        int txCount = ThreadLocalRandom.current().nextInt(80, 121);
        generateTransactions(txCount);
    }

    private int generateTransactions(int txCount) {
        try {
            List<Account> eligibleAccounts = accountRepository.findAll().stream()
                    .filter(account -> account.getStatus() == Account.AccountStatus.ACTIVE)
                    .filter(account -> account.getUser() != null)
                    .toList();

            if (eligibleAccounts.size() < 2) {
                log.error("Khong du tai khoan de sinh du lieu mock. Can it nhat 2 account ACTIVE.");
                return 0;
            }

            for (int i = 0; i < txCount; i++) {
                Account fromAcc = pickRandomAccount(eligibleAccounts, null);
                Account toAcc = pickRandomAccount(eligibleAccounts, fromAcc.getId());

                long amount = ThreadLocalRandom.current().nextLong(MIN_TRANSFER_AMOUNT, MAX_TRANSFER_AMOUNT + 1);
                LocalDateTime createdAt = LocalDateTime.now();

                Transaction pendingTx = new Transaction();
                pendingTx.setFromAccount(fromAcc);
                pendingTx.setToAccount(toAcc);
                pendingTx.setAmount((double) amount);
                pendingTx.setDescription("Mock Auto - " + createdAt);
                pendingTx.setStatus("PENDING");
                pendingTx.setCreatedAt(createdAt);

                Transaction savedTx = transactionRepository.save(pendingTx);

                TransferEvent event = TransferEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .transactionId(savedTx.getId().toString())
                        .fromAccountId(fromAcc.getId().toString())
                        .fromAccountNumber(fromAcc.getAccountNumber())
                        .senderUserId(fromAcc.getUser().getId().toString())
                        .toAccountId(toAcc.getId().toString())
                        .toAccountNumber(toAcc.getAccountNumber())
                        .receiverUserId(toAcc.getUser().getId().toString())
                        .amount(BigDecimal.valueOf(amount))
                        .description(pendingTx.getDescription())
                        .status(TransferEvent.TransferStatus.PENDING)
                        .timestamp(createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant())
                        .fromProvince(fromAcc.getUser().getProvince())
                        .fromDistrict(fromAcc.getUser().getDistrict())
                        .toProvince(toAcc.getUser().getProvince())
                        .toDistrict(toAcc.getUser().getDistrict())
                        .build();

                kafkaTemplate.send("transfer-topic", savedTx.getId().toString(), event);
            }

            log.info("Da sinh va ban {} giao dich mock vao Kafka.", txCount);
            return txCount;
        } catch (Exception e) {
            log.error("Loi khi sinh du lieu mock", e);
            return 0;
        }
    }

    private Account pickRandomAccount(List<Account> accounts, UUID excludedAccountId) {
        Account candidate;
        do {
            candidate = accounts.get(ThreadLocalRandom.current().nextInt(accounts.size()));
        } while (excludedAccountId != null && excludedAccountId.equals(candidate.getId()));
        return candidate;
    }
}
