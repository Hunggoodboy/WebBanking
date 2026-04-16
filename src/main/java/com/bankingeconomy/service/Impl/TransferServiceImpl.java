package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.event.TransferEvent;
import com.bankingeconomy.dto.request.TransferRequest;
import com.bankingeconomy.dto.response.TransferResponse;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.UserRepository;
import com.bankingeconomy.service.BalanceCacheService;
import com.bankingeconomy.service.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final BalanceCacheService balanceCacheService;

    private static final String TRANSFER_TOPIC = "transfer-topic";

    @Override
    public TransferResponse initiateTransfer(User currentUser, TransferRequest request) {

        // ── 3. Lấy tài khoản ACTIVE của user (lấy tài khoản đầu tiên ACTIVE) ──
        Account fromAccount = accountRepository
                .findByUserIdAndStatus(currentUser.getId(), Account.AccountStatus.ACTIVE)
                .stream()
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        // ── 4. Kiểm tra tài khoản đích tồn tại ────────────────────────────
        Account toAccount = accountRepository
                .findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        // ── 5. Không cho chuyển tiền vào chính mình ────────────────────────
        if (fromAccount.getAccountNumber().equals(request.getToAccountNumber())) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        // ── 6. Kiểm tra số dư (Redis cache-first, fallback DB) ─────────────
        boolean hasSufficientBalance = balanceCacheService.hasEnoughBalance(
                fromAccount.getId(),
                request.getAmount().doubleValue()
        );
        if (!hasSufficientBalance) {
            throw new AppException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        // ── 7. Build và gửi event lên Kafka ───────────────────────────────
        String txId = UUID.randomUUID().toString();

        TransferEvent event = TransferEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .transactionId(txId)
                .fromAccountId(fromAccount.getId().toString())
                .toAccountId(toAccount.getId().toString())
                .fromAccountNumber(fromAccount.getAccountNumber())
                .toAccountNumber(request.getToAccountNumber())
                .senderUserId(currentUser.getId().toString())
                .receiverUserId(toAccount.getUser().getId().toString())
                .amount(request.getAmount())
                .currency("VND")
                .description(request.getDescription())
                .status(TransferEvent.TransferStatus.PENDING)
                .timestamp(Instant.now())
                .build();

        kafkaTemplate.send(TRANSFER_TOPIC, txId, event);

        log.info("Transfer initiated: txId={} from={} to={} amount={}",
                txId,
                fromAccount.getAccountNumber(),
                request.getToAccountNumber(),
                request.getAmount()
        );

        // ── 8. Trả về response ─────────────────────────────────────────────
        return TransferResponse.builder()
                .message("Yêu cầu chuyển khoản đang được xử lý")
                .transactionId(txId)
                .build();
    }
}