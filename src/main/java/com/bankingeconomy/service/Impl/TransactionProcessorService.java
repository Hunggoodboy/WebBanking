package com.bankingeconomy.service.Impl;

import com.bankingeconomy.config.database.DbContextHolder;
import com.bankingeconomy.config.database.DbType;
import com.bankingeconomy.dto.event.TransferEvent;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.TransactionRepository;
import com.bankingeconomy.service.BalanceCacheService;
import com.bankingeconomy.service.kafka.producer.TransactionResultPublisher;
import com.bankingeconomy.utils.RegionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessorService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final BalanceCacheService balanceCacheService;
    private final TransactionResultPublisher transactionResultPublisher;

    public void processTransaction(TransferEvent event) {
        UUID txId = UUID.fromString(event.getTransactionId());
        log.info("Bắt đầu xử lý giao dịch: txId={} eventId={}", txId, event.getEventId());

        // 1. Xác định vị trí DB
        DbType senderDb = RegionUtil.getRegionByProvince(event.getFromProvince());
        DbType receiverDb = RegionUtil.getRegionByProvince(event.getToProvince());

        // 2. Lấy thông tin giao dịch từ Trạm gửi
        DbContextHolder.setCurrentDb(senderDb);
        Transaction tx = null;
        try {
            tx = transactionRepository.findById(txId)
                                      .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch trong DB: " + txId));

            if (!"PENDING".equals(tx.getStatus())) {
                log.warn("Giao dịch txId={} đã được xử lý trước đó (status={}). Bỏ qua.", txId, tx.getStatus());
                return;
            }

            transactionRepository.updateStatus(txId, "PROCESSING");
            event.setStatus(TransferEvent.TransferStatus.PROCESSING);
        } finally {
            DbContextHolder.clear();
        }

        boolean debitCompleted = false;
        try {
            // Bước 3.1: Trừ tiền Trạm Gửi (ĐÃ THÊM 'event')
            handleDebit(tx, event, senderDb);
            debitCompleted = true;

            // Bước 3.2: Cộng tiền Trạm Nhận (ĐÃ THÊM 'event')
            handleCredit(tx, event, receiverDb);

            // Bước 3.3: Cập nhật SUCCESS ở Trạm Gửi
            updateTransactionStatus(txId, senderDb, "SUCCESS");
            event.setStatus(TransferEvent.TransferStatus.COMPLETED);
            log.info("Giao dịch hoàn tất thành công: txId={}", txId);

        } catch (Exception e) {
            log.error("Xử lý giao dịch thất bại: txId={} | Lỗi: {}", txId, e.getMessage());

            // 4. Nếu đã trừ tiền mà lỗi thì Rollback hoàn tiền (ĐÃ THÊM 'event')
            if (debitCompleted) {
                handleRollback(tx, event, senderDb);
            }

            // Đánh dấu FAILED
            updateTransactionStatus(txId, senderDb, "FAILED");
            event.setStatus(TransferEvent.TransferStatus.FAILED);
        }
    }

    private void handleDebit(Transaction tx, TransferEvent event, DbType senderDb) {
        DbContextHolder.setCurrentDb(senderDb);
        try {
            UUID fromAccountId = UUID.fromString(event.getFromAccountId());
            Account from = accountRepository.findById(fromAccountId)
                                            .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

            if (from.getBalance() < tx.getAmount()) {
                throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
            }

            from.setBalance(from.getBalance() - tx.getAmount());
            accountRepository.save(from);
            balanceCacheService.evictBalance(from.getId());

            log.info("Đã trừ tiền: account={} | balance mới={}", from.getAccountNumber(), from.getBalance());
        } finally {
            DbContextHolder.clear();
        }
    }

    private void handleCredit(Transaction tx, TransferEvent event, DbType receiverDb) {
        DbContextHolder.setCurrentDb(receiverDb);
        try {
            // 2. Sử dụng 'event' để lấy ID người nhận an toàn, tránh bị Null
            UUID toAccountId = UUID.fromString(event.getToAccountId());
            UUID fromAccountId = UUID.fromString(event.getFromAccountId());

            Account to = accountRepository.findById(toAccountId)
                                          .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

            to.setBalance(to.getBalance() + tx.getAmount());
            accountRepository.save(to);
            balanceCacheService.evictBalance(to.getId());

            log.info("Đã cộng tiền: account={} | balance mới={}", to.getAccountNumber(), to.getBalance());

            // 3. Ghi lịch sử giao dịch vào trạm nhận (Lưu ý: fromAccount để null)
            if (!transactionRepository.existsById(tx.getId())) {
                transactionRepository.insertTransactionCopy(
                        tx.getId(), tx.getAmount(), tx.getDescription(),
                        "SUCCESS", tx.getCreatedAt(), to.getId(), fromAccountId
                );
                log.info("Đã lưu bản sao giao dịch vào trạm nhận bằng Native SQL.");
            }
        } finally {
            DbContextHolder.clear();
        }
    }

    private void handleRollback(Transaction tx, TransferEvent event, DbType senderDb) {
        log.warn("Đang thực hiện hoàn tiền cho giao dịch lỗi txId={}", tx.getId());
        DbContextHolder.setCurrentDb(senderDb);
        try {
            // LẤY ID TỪ EVENT THAY VÌ TỪ TX
            UUID fromAccountId = UUID.fromString(event.getFromAccountId());
            Account from = accountRepository.findById(fromAccountId).orElse(null);
            if (from != null) {
                from.setBalance(from.getBalance() + tx.getAmount());
                accountRepository.save(from);
                balanceCacheService.evictBalance(from.getId());
                log.info("Hoàn tiền thành công cho tài khoản: {}", from.getAccountNumber());
            }
        } catch (Exception e) {
            log.error("Lỗi nghiêm trọng: không thể hoàn tiền cho txId={}", tx.getId(), e);
        } finally {
            DbContextHolder.clear();
        }
    }

    private void updateTransactionStatus(UUID txId, DbType senderDb, String status) {
        DbContextHolder.setCurrentDb(senderDb);
        try {
            transactionRepository.updateStatus(txId, status);
        } finally {
            DbContextHolder.clear();
        }
    }
}