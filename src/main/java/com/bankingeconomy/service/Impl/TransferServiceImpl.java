package com.bankingeconomy.service.Impl;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private static final String TRANSFER_TOPIC = "transfer-topic";

    @Override
    @Transactional
    public TransferResponse initiateTransfer(User currentUser, TransferRequest request) {

        Account fromAccount = accountRepository
                .findByUserIdAndStatus(currentUser.getId(), Account.AccountStatus.ACTIVE)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        // 3. Kiểm tra tài khoản đích
        Account toAccount = accountRepository
                .findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (fromAccount.getAccountNumber().equals(request.getToAccountNumber())) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        // 4. Kiểm tra nhanh số dư qua Redis (Để chặn sớm yêu cầu không hợp lệ)
        boolean sufficient = balanceCacheService.hasEnoughBalance(
                fromAccount.getId(),
                request.getAmount().doubleValue()
        );
        if (!sufficient) {
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        // 5. LƯU PENDING TRANSACTION (Bước chốt chặn của V2)
        Transaction pendingTx = new Transaction();
        pendingTx.setFromAccount(fromAccount);
        pendingTx.setToAccount(toAccount);
        pendingTx.setAmount(request.getAmount().doubleValue());
        pendingTx.setDescription(request.getDescription());
        pendingTx.setStatus("PENDING");
        pendingTx.setCreatedAt(LocalDateTime.now());
        Transaction saved = transactionRepository.save(pendingTx);

        // Lấy ID thật từ DB để làm khóa liên kết cho Kafka
        String txId = saved.getId().toString();

        User toUser = userRepository.findById(toAccount.getUser().getId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        TransferEvent event = TransferEvent.builder()
                .eventId(UUID.randomUUID().toString()) // ID duy nhất của message
                .transactionId(txId)                   // ID thực tế trong DB
                .fromAccountId(fromAccount.getId().toString())
                .toAccountId(toAccount.getId().toString())
                .fromAccountNumber(fromAccount.getAccountNumber())
                .toAccountNumber(toAccount.getAccountNumber())
                .senderUserId(currentUser.getId().toString())
                .receiverUserId(toAccount.getUser().getId().toString())
                .fromProvince(currentUser.getProvince())
                .fromDistrict(currentUser.getDistrict())
                .toProvince(toUser.getProvince())
                .toDistrict(toUser.getDistrict())
                .amount(request.getAmount())
                .description(request.getDescription())
                .status(TransferEvent.TransferStatus.PENDING)
                .timestamp(Instant.now())
                .build();

        // 7. Bắn Kafka
        kafkaTemplate.send(TRANSFER_TOPIC, txId, event);

        log.info("Yêu cầu chuyển tiền đã được ghi nhận: txId={} | Amount={}", txId, request.getAmount());

        return TransferResponse.builder()
                .message("Yêu cầu chuyển khoản đang được xử lý")
                .transactionId(txId)
                .build();
    }
}