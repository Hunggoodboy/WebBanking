package com.bankingeconomy.service.Impl;

import com.bankingeconomy.config.database.DbContextHolder;
import com.bankingeconomy.config.database.DbType;
import com.bankingeconomy.dto.event.TransferEvent;
import com.bankingeconomy.dto.request.TransferRequest;
import com.bankingeconomy.dto.response.TransferResponse;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.TransactionRepository;
import com.bankingeconomy.repository.UserRepository;
import com.bankingeconomy.service.BalanceCacheService;
import com.bankingeconomy.service.TransferService;
import com.bankingeconomy.utils.RegionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceCacheService balanceCacheService;
    private final PlatformTransactionManager transactionManager;

    private static final String TRANSFER_TOPIC = "transfer-topic";

    @Override
    public TransferResponse initiateTransfer(User currentUser, TransferRequest request) {

        Account fromAccount = accountRepository
                .findByUserIdAndStatus(currentUser.getId(), Account.AccountStatus.ACTIVE)
                .stream().findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        Account toAccount = accountRepository
                .findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (fromAccount.getAccountNumber().equals(request.getToAccountNumber())) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        if (!balanceCacheService.hasEnoughBalance(fromAccount.getId(), request.getAmount().doubleValue())) {
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }




        User toUser = userRepository.findById(toAccount.getUser().getId())
                                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        TransferEvent event = TransferEvent.builder()
                                           .eventId(UUID.randomUUID().toString())
                                           .transactionId("")  // saveAndPublish sẽ set lại
                                           .fromAccountId(fromAccount.getId().toString())
                                           .toAccountId(toAccount.getId().toString())
                                           .fromAccountNumber(fromAccount.getAccountNumber())
                                           .toAccountNumber(toAccount.getAccountNumber())
                                           .senderUserId(currentUser.getId().toString())
                                           .receiverUserId(toUser.getId().toString())
                                           .fromProvince(currentUser.getProvince())
                                           .fromDistrict(currentUser.getDistrict())
                                           .toProvince(toUser.getProvince())
                                           .toDistrict(toUser.getDistrict())
                                           .amount(request.getAmount())
                                           .description(request.getDescription())
                                           .status(TransferEvent.TransferStatus.PENDING)
                                           .timestamp(Instant.now())
                                           .build();

        DbType targetDb = RegionUtil.getRegionByProvince(currentUser.getProvince());

        log.info("=== DEBUG: CHUẨN BỊ LƯU GIAO DỊCH ===");
        log.info("1. Tỉnh/Thành người gửi: {}", currentUser.getProvince());
        log.info("2. Target DB mong muốn bẻ ghi: {}", targetDb);

        DbContextHolder.setCurrentDb(targetDb);
        Transaction saved = null;
        try {
            log.info("3. Ép Spring tạo Transaction mới toanh để vứt bỏ kết nối CENTRAL cũ...");

            // Khởi tạo Transaction "Bọc thép"
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            // Thực thi lưu DB bên trong bong bóng cách ly
            saved = transactionTemplate.execute(status -> {
                log.info("4. Đã vào Transaction mới! BỘ ĐỊNH TUYẾN BẮT BUỘC PHẢI CHỌN LẠI DB NGAY BÂY GIỜ.");

                Transaction pendingTx = new Transaction();
                pendingTx.setFromAccount(accountRepository.getReferenceById(fromAccount.getId()));
                pendingTx.setToAccount(accountRepository.getReferenceById(toAccount.getId()));
                pendingTx.setAmount(request.getAmount().doubleValue());
                pendingTx.setDescription(request.getDescription());
                pendingTx.setStatus("PENDING");
                pendingTx.setCreatedAt(LocalDateTime.now());

                return transactionRepository.saveAndFlush(pendingTx);
            });

            log.info("5. Lệnh save() thực thi xong an toàn! ID sinh ra: {}", saved.getId());

        } catch (Exception e) {
            log.error("❌ LỖI KHI LƯU DB: ", e);
            throw e;
        } finally {
            // Hạ cờ trả lại kết nối sạch
            DbContextHolder.clear();
        }

        // Bước 2: Lấy ID thật vừa lưu
        String txId = saved.getId().toString();
        event.setTransactionId(txId);

        // Bước 3: Đứng ở ngoài vùng DB, ung dung bắn Kafka
        log.info("6. Giao dịch đã nằm trong ổ cứng MID, chuẩn bị bắn Kafka cho txId: {}", txId);
        kafkaTemplate.send(TRANSFER_TOPIC, txId, event);

        // --- KẾT THÚC ---

        return TransferResponse.builder()
                               .message("Yêu cầu chuyển khoản đang được xử lý")
                               .transactionId(txId)
                               .build();
    }
}