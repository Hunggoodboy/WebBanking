package com.bankingeconomy.service.consume;

import com.bankingeconomy.entity.Account;
import com.bankingeconomy.event.TransferEvent;
import com.bankingeconomy.event.TransferEvent.TransferStatus;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.repository.AccountRepository;
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

    private final BalanceCacheService balanceCacheService;
    private final AccountRepository accountRepository;

    private static final ThreadLocal<Boolean> debitCompleted = ThreadLocal.withInitial(() -> false);

    @Transactional
    public void processTransaction(TransferEvent event) {

        log.info("Start processing eventId={} status={}", event.getEventId(), event.getStatus());

        try {
            debitCompleted.set(false);

            event.setStatus(TransferStatus.PROCESSING);

            handleDebit(event);

            handleCredit(event);

            event.setStatus(TransferStatus.COMPLETED);
            log.info("✅ Transaction completed: eventId={}", event.getEventId());

        } catch (Exception e) {
            event.setStatus(TransferStatus.FAILED);
            log.error("❌ Transaction failed: eventId={}, error={}", event.getEventId(), e.getMessage(), e);

            handleRollback(event);
        } finally {
            debitCompleted.remove();
        }
    }

    private void handleDebit(TransferEvent event) {
        UUID fromAccountId = UUID.fromString(event.getFromAccountId());
        double amount = event.getAmount().doubleValue();

        log.info("Debiting {} from account {}", amount, fromAccountId);

        boolean hasEnough = balanceCacheService.hasEnoughBalance(fromAccountId, amount);
        if (!hasEnough) {
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        Account fromAccount = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        double currentBalance = fromAccount.getBalance();
        double newBalance = currentBalance - amount;

        if (newBalance < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        balanceCacheService.updateBalance(fromAccountId, newBalance);
        debitCompleted.set(true);

        log.info("✅ Debited {} from account {} | new balance = {}", amount, fromAccountId, newBalance);
    }

    private void handleCredit(TransferEvent event) {
        UUID toAccountId = UUID.fromString(event.getToAccountId());
        double amount = event.getAmount().doubleValue();

        log.info("Crediting {} to account {}", amount, toAccountId);

        Account toAccount = accountRepository.findById(toAccountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        double currentBalance = toAccount.getBalance();
        double newBalance = currentBalance + amount;

        balanceCacheService.updateBalance(toAccountId, newBalance);

        log.info("✅ Credited {} to account {} | new balance = {}", amount, toAccountId, newBalance);
    }

    private void handleRollback(TransferEvent event) {
        log.warn("Rolling back transaction: eventId={}", event.getEventId());

        try {
            UUID fromAccountId = UUID.fromString(event.getFromAccountId());
            UUID toAccountId = UUID.fromString(event.getToAccountId());
            double amount = event.getAmount().doubleValue();

            if (debitCompleted.get()) {
                Account fromAccount = accountRepository.findById(fromAccountId)
                        .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

                double refundBalance = fromAccount.getBalance() + amount;
                balanceCacheService.updateBalance(fromAccountId, refundBalance);

                log.info("✅ Rollback: Đã hoàn {} cho account {}", amount, fromAccountId);
            }

            balanceCacheService.evictBalance(fromAccountId);
            balanceCacheService.evictBalance(toAccountId);

            log.info("🗑️ Đã evict cache cho cả 2 tài khoản: {} và {}", fromAccountId, toAccountId);

        } catch (Exception e) {
            log.error("❌ Rollback thất bại: {}", e.getMessage(), e);
        }
    }
}
