package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.request.BeneficiaryRequest;
import com.bankingeconomy.dto.response.BeneficiaryLookupResponse;
import com.bankingeconomy.dto.response.BeneficiaryResponse;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.Beneficiary;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.BeneficiaryRepository;
import com.bankingeconomy.repository.UserRepository;
import com.bankingeconomy.service.BeneficiaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BeneficiaryServiceImpl implements BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    @Override
    public List<BeneficiaryResponse> getMyBeneficiaries(User user, String keyword) {
        String normalizedKeyword = String.valueOf(keyword == null ? "" : keyword).trim().toLowerCase(Locale.ROOT);

        return beneficiaryRepository.findByUserId(user.getId()).stream()
                .sorted(Comparator.comparing(Beneficiary::getId).reversed())
                .map(this::toResponse)
                .filter(item -> normalizedKeyword.isBlank() || matchesKeyword(item, normalizedKeyword))
                .toList();
    }

    @Override
    public BeneficiaryResponse getMyBeneficiary(User user, UUID beneficiaryId) {
        return toResponse(requireOwnedBeneficiary(user, beneficiaryId));
    }

    @Override
    public BeneficiaryLookupResponse lookupUserByUuid(User user, UUID targetUserId) {
        if (targetUserId == null) {
            throw new IllegalArgumentException("Vui lòng nhập UUID người dùng cần tra cứu");
        }

        if (user.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("Không thể thêm chính bạn vào danh bạ người thụ hưởng");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với UUID đã nhập"));
        Account targetAccount = findPrimaryActiveAccount(targetUserId);
        boolean alreadySaved = beneficiaryRepository.findByUserIdAndTargetAccountId(user.getId(), targetAccount.getId()).isPresent();

        return BeneficiaryLookupResponse.builder()
                .userId(targetUser.getId())
                .accountId(targetAccount.getId())
                .fullName(targetUser.getFullName())
                .email(targetUser.getEmail())
                .phone(targetUser.getPhone())
                .accountNumber(targetAccount.getAccountNumber())
                .alreadySaved(alreadySaved)
                .build();
    }

    @Override
    public ResponseData<BeneficiaryResponse> createBeneficiary(User user, BeneficiaryRequest request) {
        BeneficiaryLookupResponse lookup = lookupUserByUuid(user, request != null ? request.getTargetUserId() : null);

        if (lookup.isAlreadySaved()) {
            return new ResponseData<>(HttpStatus.BAD_REQUEST.value(), "Người thụ hưởng này đã có trong danh bạ");
        }

        Account targetAccount = accountRepository.findById(lookup.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản người thụ hưởng"));

        Beneficiary beneficiary = beneficiaryRepository.save(Beneficiary.builder()
                .user(user)
                .targetAccount(targetAccount)
                .build());

        return new ResponseData<>(
                HttpStatus.CREATED.value(),
                "Thêm người thụ hưởng thành công",
                toResponse(beneficiary)
        );
    }

    @Override
    public ResponseData<BeneficiaryResponse> updateBeneficiary(User user, UUID beneficiaryId, BeneficiaryRequest request) {
        Beneficiary beneficiary = requireOwnedBeneficiary(user, beneficiaryId);
        BeneficiaryLookupResponse lookup = lookupUserByUuid(user, request != null ? request.getTargetUserId() : null);

        if (!beneficiary.getTargetAccount().getId().equals(lookup.getAccountId())
                && beneficiaryRepository.findByUserIdAndTargetAccountId(user.getId(), lookup.getAccountId()).isPresent()) {
            return new ResponseData<>(HttpStatus.BAD_REQUEST.value(), "Người thụ hưởng này đã có trong danh bạ");
        }

        Account targetAccount = accountRepository.findById(lookup.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản người thụ hưởng"));

        beneficiary.setTargetAccount(targetAccount);
        beneficiaryRepository.save(beneficiary);

        return new ResponseData<>(
                HttpStatus.OK.value(),
                "Cập nhật người thụ hưởng thành công",
                toResponse(beneficiary)
        );
    }

    @Override
    public ResponseData<Void> deleteBeneficiary(User user, UUID beneficiaryId) {
        Beneficiary beneficiary = requireOwnedBeneficiary(user, beneficiaryId);
        beneficiaryRepository.delete(beneficiary);
        return new ResponseData<>(HttpStatus.OK.value(), "Xóa người thụ hưởng thành công");
    }

    private Beneficiary requireOwnedBeneficiary(User user, UUID beneficiaryId) {
        return beneficiaryRepository.findById(beneficiaryId)
                .filter(item -> item.getUser() != null && user.getId().equals(item.getUser().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người thụ hưởng trong danh bạ của bạn"));
    }

    private Account findPrimaryActiveAccount(UUID userId) {
        return accountRepository.findByUserIdAndStatus(userId, Account.AccountStatus.ACTIVE).stream()
                .sorted(Comparator.comparing(Account::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Người dùng này chưa có tài khoản hoạt động để thêm vào danh bạ"));
    }

    private BeneficiaryResponse toResponse(Beneficiary beneficiary) {
        Account targetAccount = beneficiary.getTargetAccount();
        User targetUser = targetAccount != null ? targetAccount.getUser() : null;

        return BeneficiaryResponse.builder()
                .id(beneficiary.getId())
                .targetUserId(targetUser != null ? targetUser.getId() : null)
                .targetAccountId(targetAccount != null ? targetAccount.getId() : null)
                .fullName(targetUser != null ? targetUser.getFullName() : "Không rõ người nhận")
                .email(targetUser != null ? targetUser.getEmail() : "")
                .phone(targetUser != null ? targetUser.getPhone() : "")
                .accountNumber(targetAccount != null ? targetAccount.getAccountNumber() : "")
                .build();
    }

    private boolean matchesKeyword(BeneficiaryResponse item, String keyword) {
        return contains(item.getFullName(), keyword)
                || contains(item.getEmail(), keyword)
                || contains(item.getPhone(), keyword)
                || contains(item.getAccountNumber(), keyword)
                || contains(item.getTargetUserId(), keyword);
    }

    private boolean contains(Object value, String keyword) {
        return String.valueOf(value == null ? "" : value)
                .toLowerCase(Locale.ROOT)
                .contains(keyword);
    }
}
