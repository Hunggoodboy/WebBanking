package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.event.TransferEvent;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.TransactionRepository;
import com.bankingeconomy.service.BalanceCacheService;
import com.bankingeconomy.service.kafka.producer.TransactionResultPublisher;
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
    private final TransactionResultPublisher transactionResultPublisher;

    @Transactional
    public void processTransaction(TransferEvent event) {
        UUID txId = UUID.fromString(event.getTransactionId());
        log.info("Bat dau xu ly giao dich: txId={} eventId={}", txId, event.getEventId());

        Transaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay giao dich trong DB: " + txId));

        if (!"PENDING".equals(tx.getStatus())) {
            log.warn("Giao dich txId={} da duoc xu ly truoc do (status={}). Bo qua.", txId, tx.getStatus());
            return;
        }

        transactionRepository.updateStatus(txId, "PROCESSING");
        event.setStatus(TransferEvent.TransferStatus.PROCESSING);

        boolean debitCompleted = false;
        try {
            handleDebit(tx);
            debitCompleted = true;
            handleCredit(tx);

            transactionRepository.updateStatus(txId, "SUCCESS");
            event.setStatus(TransferEvent.TransferStatus.COMPLETED);
            log.info("Giao dich hoan tat thanh cong: txId={}", txId);
        } catch (Exception e) {
            log.error("Xu ly giao dich that bai: txId={} | Loi: {}", txId, e.getMessage());

            if (debitCompleted) {
                handleRollback(tx);
            }

            transactionRepository.updateStatus(txId, "FAILED");
            event.setStatus(TransferEvent.TransferStatus.FAILED);
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

        log.info("Da tru tien: account={} | balance moi={}", from.getAccountNumber(), from.getBalance());
    }

    private void handleCredit(Transaction tx) {
        Account to = accountRepository.findById(tx.getToAccount().getId())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        to.setBalance(to.getBalance() + tx.getAmount());
        accountRepository.save(to);
        balanceCacheService.evictBalance(to.getId());

        log.info("Da cong tien: account={} | balance moi={}", to.getAccountNumber(), to.getBalance());
    }

    private void handleRollback(Transaction tx) {
        log.warn("Dang thuc hien hoan tien cho giao dich loi txId={}", tx.getId());
        try {
            Account from = accountRepository.findById(tx.getFromAccount().getId()).orElse(null);
            if (from != null) {
                from.setBalance(from.getBalance() + tx.getAmount());
                accountRepository.save(from);
                balanceCacheService.evictBalance(from.getId());
            }
        } catch (Exception e) {
            log.error("Loi nghiem trong: khong the hoan tien cho txId={}", tx.getId(), e);
        }
    }
}
