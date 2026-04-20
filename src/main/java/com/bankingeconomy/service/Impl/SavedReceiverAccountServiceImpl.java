package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.request.SavedReceiverAccountRequest;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.dto.response.SavedReceiverAccountResponse;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.SavedReceiverAccount;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.SavedReceiverAccountRepository;
import com.bankingeconomy.service.SavedReceiverAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SavedReceiverAccountServiceImpl implements SavedReceiverAccountService {

    private static final int MAX_SAVED_RECEIVERS = 6;

    private final SavedReceiverAccountRepository savedReceiverAccountRepository;
    private final AccountRepository accountRepository;

    @Override
    public List<SavedReceiverAccountResponse> getMySavedReceivers(User user) {
        return savedReceiverAccountRepository.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .limit(MAX_SAVED_RECEIVERS)
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ResponseData<SavedReceiverAccountResponse> saveReceiver(User user, SavedReceiverAccountRequest request) {
        String accountNumber = request != null && request.getAccountNumber() != null
                ? request.getAccountNumber().trim()
                : "";

        if (accountNumber.isBlank()) {
            return new ResponseData<>(HttpStatus.BAD_REQUEST.value(), "Vui lòng nhập số tài khoản người nhận");
        }

        if (!accountNumber.matches("\\d{6,20}")) {
            return new ResponseData<>(HttpStatus.BAD_REQUEST.value(), "Số tài khoản người nhận không hợp lệ");
        }

        Account targetAccount = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản người nhận"));

        if (targetAccount.getUser() != null && user.getId().equals(targetAccount.getUser().getId())) {
            return new ResponseData<>(HttpStatus.BAD_REQUEST.value(), "Không thể lưu tài khoản của chính bạn vào danh sách người nhận gần đây");
        }

        SavedReceiverAccount savedReceiver = savedReceiverAccountRepository
                .findByUserIdAndTargetAccountId(user.getId(), targetAccount.getId())
                .orElseGet(() -> SavedReceiverAccount.builder()
                        .user(user)
                        .targetAccount(targetAccount)
                        .build());

        savedReceiver.setTargetAccount(targetAccount);
        savedReceiver.setUpdatedAt(LocalDateTime.now());
        SavedReceiverAccount persisted = savedReceiverAccountRepository.save(savedReceiver);

        trimOverflow(user);

        return new ResponseData<>(
                HttpStatus.OK.value(),
                "Lưu tài khoản người nhận gần đây thành công",
                toResponse(persisted)
        );
    }

    private void trimOverflow(User user) {
        List<SavedReceiverAccount> items = savedReceiverAccountRepository.findByUserIdOrderByUpdatedAtDesc(user.getId());
        if (items.size() <= MAX_SAVED_RECEIVERS) {
            return;
        }

        savedReceiverAccountRepository.deleteAll(items.subList(MAX_SAVED_RECEIVERS, items.size()));
    }

    private SavedReceiverAccountResponse toResponse(SavedReceiverAccount item) {
        Account targetAccount = item.getTargetAccount();
        User targetUser = targetAccount != null ? targetAccount.getUser() : null;

        return SavedReceiverAccountResponse.builder()
                .id(item.getId())
                .targetUserId(targetUser != null ? targetUser.getId() : null)
                .targetAccountId(targetAccount != null ? targetAccount.getId() : null)
                .accountNumber(targetAccount != null ? targetAccount.getAccountNumber() : "")
                .accountHolderName(targetUser != null ? targetUser.getFullName() : "Không rõ chủ tài khoản")
                .email(targetUser != null ? targetUser.getEmail() : "")
                .phone(targetUser != null ? targetUser.getPhone() : "")
                .status(targetAccount != null && targetAccount.getStatus() != null ? targetAccount.getStatus().name() : "")
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
