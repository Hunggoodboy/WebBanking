package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.event.TransferEvent;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.Transaction;

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
        // 1. Chuyển đổi ID từ Event
        UUID txId = UUID.fromString(event.getTransactionId());
        log.info("Bắt đầu xử lý giao dịch: txId={} eventId={}", txId, event.getEventId());

        // 2. Kiểm tra Idempotency (Chống xử lý trùng lặp)
        Transaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch trong DB: " + txId));

        if (!"PENDING".equals(tx.getStatus())) {
            log.warn("Giao dịch txId={} đã được xử lý trước đó (status={}). Bỏ qua.", txId, tx.getStatus());
            return;
        }

        // 3. Cập nhật trạng thái đang xử lý
        transactionRepository.updateStatus(txId, "PROCESSING");
        event.setStatus(TransferEvent.TransferStatus.PROCESSING);

        try {
            // 4. Thực hiện trừ tiền và cộng tiền
            handleDebit(tx);
            handleCredit(tx);

            // 5. Cập nhật thành công cho cả DB và Kafka Event
            transactionRepository.updateStatus(txId, "SUCCESS");
            event.setStatus(TransferEvent.TransferStatus.COMPLETED);

            log.info("Giao dịch hoàn tất thành công: txId={}", txId);

        } catch (Exception e) {
            log.error("Xử lý giao dịch thất bại: txId={} | Lỗi: {}", txId, e.getMessage());

            // 6. Rollback nghiệp vụ & cập nhật trạng thái lỗi
            handleRollback(tx);
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

        // Trừ tiền DB
        from.setBalance(from.getBalance() - tx.getAmount());
        accountRepository.save(from);

        // Xóa Cache Redis để lần sau khách xem số dư sẽ lấy số mới từ DB
        balanceCacheService.evictBalance(from.getId());

        log.info("Đã trừ tiền: account={} | balance mới={}", from.getAccountNumber(), from.getBalance());
    }

    private void handleCredit(Transaction tx) {
        Account to = accountRepository.findById(tx.getToAccount().getId())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        // Cộng tiền DB
        to.setBalance(to.getBalance() + tx.getAmount());
        accountRepository.save(to);

        // Cập nhật Cache Redis cho người nhận
        balanceCacheService.evictBalance(to.getId());

        log.info("Đã cộng tiền: account={} | balance mới={}", to.getAccountNumber(), to.getBalance());
    }

    private void handleRollback(Transaction tx) {
        log.warn("Đang thực hiện hoàn tiền cho giao dịch lỗi txId={}", tx.getId());
        try {
            Account from = accountRepository.findById(tx.getFromAccount().getId()).orElse(null);
            if (from != null) {
                from.setBalance(from.getBalance() + tx.getAmount());
                accountRepository.save(from);
                balanceCacheService.evictBalance(from.getId());
            }
        } catch (Exception e) {
            log.error("LỖI NGHIÊM TRỌNG: Không thể hoàn tiền cho txId={}", tx.getId());
        }
    }
}