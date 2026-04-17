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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
    public TransferResponse initiateTransfer(TransferRequest request) {

        // ── 1. Lấy user hiện tại từ JWT ────────────────────────────────────
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // ── 2. Lấy tài khoản nguồn ACTIVE của user ─────────────────────────
        Account fromAccount = accountRepository
                .findByUserIdAndStatus(currentUser.getId(), Account.AccountStatus.ACTIVE)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        // ── 3. Kiểm tra tài khoản đích tồn tại ────────────────────────────
        Account toAccount = accountRepository
                .findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        // ── 4. Không cho chuyển vào chính mình ────────────────────────────
        if (fromAccount.getAccountNumber().equals(request.getToAccountNumber())) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        // ── 5. Kiểm tra số dư (Redis cache-first, fallback DB) ─────────────
        boolean sufficient = balanceCacheService.hasEnoughBalance(
                fromAccount.getId(),
                request.getAmount().doubleValue()
        );
        if (!sufficient) {
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        // ── 6. Lưu giao dịch PENDING vào DB trước khi bắn Kafka ───────────
        Transaction pendingTx = new Transaction();
        pendingTx.setFromAccount(fromAccount);
        pendingTx.setToAccount(toAccount);
        pendingTx.setAmount(request.getAmount().doubleValue());
        pendingTx.setDescription(request.getDescription());
        pendingTx.setStatus("PENDING");
        pendingTx.setCreatedAt(LocalDateTime.now());
        Transaction saved = transactionRepository.save(pendingTx);

        String txId = saved.getId().toString();

        log.info("Saved PENDING transaction: txId={} from={} to={} amount={}",
                txId, fromAccount.getAccountNumber(),
                toAccount.getAccountNumber(), request.getAmount());

        // ── 7. Build event và bắn lên Kafka ───────────────────────────────
        TransferEvent event = TransferEvent.builder()
                .transactionId(txId)
                .fromAccountNumber(fromAccount.getAccountNumber())
                .toAccountNumber(request.getToAccountNumber())
                .amount(request.getAmount())
                .description(request.getDescription())
                .status(TransferEvent.TransferStatus.PENDING)
                .build();

        kafkaTemplate.send(TRANSFER_TOPIC, txId, event);

        log.info("Sent Kafka event: topic={} txId={}", TRANSFER_TOPIC, txId);

        return TransferResponse.builder()
                .message("Yêu cầu chuyển khoản đang được xử lý")
                .transactionId(txId)
                .build();
    }
}