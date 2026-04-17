package com.bankingeconomy.service.consumer;

import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.event.TransferEvent;
import com.bankingeconomy.event.TransferEvent.TransferStatus;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.TransactionRepository;
import com.bankingeconomy.service.BalanceCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessorService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final BalanceCacheService balanceCacheService;

    @Transactional
    public void processTransaction(TransferEvent event) {

        // transactionId là UUID của Transaction đã lưu PENDING trong DB
        UUID txId = UUID.fromString(event.getTransactionId());

        log.info("Processing txId={} status={}", txId, event.getStatus());

        // ── IDEMPOTENCY CHECK ──────────────────────────────────────────────
        // Nếu Kafka re-deliver message, status lúc này sẽ != PENDING
        // → bỏ qua, không trừ tiền lần 2
        Transaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + txId));

        if (!"PENDING".equals(tx.getStatus())) {
            log.warn("Idempotency guard: txId={} status={} already processed. Skipping.",
                    txId, tx.getStatus());
            return;
        }

        // ── UPDATE → PROCESSING ────────────────────────────────────────────
        transactionRepository.updateStatus(txId, "PROCESSING");
        event.setStatus(TransferStatus.PROCESSING);

        try {
            handleDebit(tx);
            handleCredit(tx);

            transactionRepository.updateStatus(txId, "SUCCESS");
            event.setStatus(TransferStatus.COMPLETED);

            log.info("Transaction completed: txId={}", txId);

        } catch (Exception e) {
            log.error("Transaction failed: txId={} error={}", txId, e.getMessage(), e);
            handleRollback(tx);
            transactionRepository.updateStatus(txId, "FAILED");
            event.setStatus(TransferStatus.FAILED);
        }
    }

    private void handleDebit(Transaction tx) {
        Account from = accountRepository.findById(tx.getFromAccount().getId())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (from.getBalance() < tx.getAmount()) {
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        from.setBalance(from.getBalance() - tx.getAmount());
        accountRepository.save(from);
        balanceCacheService.evictBalance(from.getId());

        log.info("Debited {} from={} newBalance={}",
                tx.getAmount(), from.getAccountNumber(), from.getBalance());
    }

    private void handleCredit(Transaction tx) {
        Account to = accountRepository.findById(tx.getToAccount().getId())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        to.setBalance(to.getBalance() + tx.getAmount());
        accountRepository.save(to);
        balanceCacheService.evictBalance(to.getId());

        log.info("Credited {} to={} newBalance={}",
                tx.getAmount(), to.getAccountNumber(), to.getBalance());
    }

    private void handleRollback(Transaction tx) {
        log.warn("Rolling back txId={}", tx.getId());
        try {
            Account from = accountRepository.findById(tx.getFromAccount().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

            from.setBalance(from.getBalance() + tx.getAmount());
            accountRepository.save(from);
            balanceCacheService.evictBalance(from.getId());

            log.warn("Rollback done: refunded {} to={}", tx.getAmount(), from.getAccountNumber());

        } catch (Exception e) {
            log.error("CRITICAL: Rollback failed txId={}. Manual intervention required! error={}",
                    tx.getId(), e.getMessage(), e);
        }
    }
}