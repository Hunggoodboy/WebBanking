package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.request.ProfileUpdateRequest;
import com.bankingeconomy.dto.request.RegisterRequest;
import com.bankingeconomy.dto.response.RegisterResponse;
import com.bankingeconomy.dto.response.UserResponseDTO;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.UserRepository;
import com.bankingeconomy.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final double DEFAULT_INITIAL_BALANCE = 100_000_000.0;

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public RegisterResponse register(RegisterRequest request) {
        RegisterRequest normalizedRequest = prepareRegisterRequest(request);
        validateRegisterRequest(normalizedRequest);

        if (userRepository.existsByEmail(normalizedRequest.getEmail())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }
        if (userRepository.existsByPhone(normalizedRequest.getPhone())) {
            throw new AppException(ErrorCode.PHONE_EXISTED);
        }
        if (userRepository.existsByIdentityCard(normalizedRequest.getIdentityCard())) {
            throw new AppException(ErrorCode.IDENTITY_CARD_EXISTED);
        }

        User user = User.builder()
                .email(normalizedRequest.getEmail())
                .password(passwordEncoder.encode(normalizedRequest.getPassword()))
                .fullName(normalizedRequest.getFullName())
                .province(normalizedRequest.getProvince())
                .district(normalizedRequest.getDistrict())
                .phone(normalizedRequest.getPhone())
                .identityCard(normalizedRequest.getIdentityCard())
                .role(User.Role.CUSTOMER)
                .build();

        try {
            userRepository.saveAndFlush(user);
            Account account = createPrimaryAccount(user);
            return buildRegisterResponse(user, account);
        } catch (RuntimeException ex) {
            throw resolveRegisterException(ex);
        }
    }

    @Override
    public UserResponseDTO getCurrentUser(User currentUser) {
        ensureCurrentUser(currentUser);
        return toUserResponse(currentUser);
    }

    @Override
    public UserResponseDTO updateCurrentUser(User currentUser, ProfileUpdateRequest request) {
        ensureCurrentUser(currentUser);
        validateProfileUpdateRequest(request);

        if (!Objects.equals(normalize(currentUser.getEmail()), normalize(request.getEmail()))
                && userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        if (!Objects.equals(normalize(currentUser.getPhone()), normalize(request.getPhone()))
                && userRepository.existsByPhone(request.getPhone())) {
            throw new AppException(ErrorCode.PHONE_EXISTED);
        }

        if (!Objects.equals(normalize(currentUser.getIdentityCard()), normalize(request.getIdentityCard()))
                && userRepository.existsByIdentityCard(request.getIdentityCard())) {
            throw new AppException(ErrorCode.IDENTITY_CARD_EXISTED);
        }

        currentUser.setFullName(request.getFullName());
        currentUser.setEmail(request.getEmail());
        currentUser.setPhone(request.getPhone());
        currentUser.setIdentityCard(request.getIdentityCard());
        currentUser.setProvince(request.getProvince());
        currentUser.setDistrict(request.getDistrict());
        currentUser.setGender(request.getGender());

        try {
            userRepository.saveAndFlush(currentUser);
        } catch (RuntimeException ex) {
            throw resolveRegisterException(ex);
        }

        return toUserResponse(currentUser);
    }

    @Override
    public List<RegisterResponse> registerBulk(List<RegisterRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Danh sach dang ky khong duoc de trong");
        }

        return requests.stream()
                .map(this::register)
                .collect(Collectors.toList());
    }

    private RegisterRequest prepareRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Du lieu dang ky khong hop le");
        }

        if (isBlank(request.getConfirmPassword())) {
            request.setConfirmPassword(request.getPassword());
        }

        return request;
    }

    private Account createPrimaryAccount(User user) {
        Account account = Account.builder()
                .user(user)
                .accountNumber(generateUniqueAccountNumber())
                .balance(DEFAULT_INITIAL_BALANCE)
                .status(Account.AccountStatus.ACTIVE)
                .createdAt(new Date())
                .build();

        return accountRepository.save(account);
    }

    private String generateUniqueAccountNumber() {
        for (int attempt = 0; attempt < 20; attempt++) {
            long raw = ThreadLocalRandom.current().nextLong(100_000_000_000L, 1_000_000_000_000L);
            String candidate = String.valueOf(raw);
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }

        throw new AppException(ErrorCode.INVALID_INPUT, "Khong the sinh so tai khoan duy nhat. Vui long thu lai.");
    }

    private RegisterResponse buildRegisterResponse(User user, Account account) {
        return RegisterResponse.builder()
                .userId(user.getId())
                .accountId(account.getId())
                .email(user.getEmail())
                .accountNumber(account.getAccountNumber())
                .initialBalance(account.getBalance())
                .build();
    }

    private RuntimeException resolveRegisterException(RuntimeException ex) {
        String message = extractDeepestMessage(ex).toLowerCase(Locale.ROOT);

        if (message.contains("identity_card")) {
            return new AppException(ErrorCode.IDENTITY_CARD_EXISTED);
        }
        if (message.contains("phone")) {
            return new AppException(ErrorCode.PHONE_EXISTED);
        }
        if (message.contains("email")) {
            return new AppException(ErrorCode.USER_EXISTED);
        }
        if (message.contains("account_number")) {
            return new AppException(ErrorCode.INVALID_INPUT, "So tai khoan bi trung. Vui long thu lai.");
        }
        if (message.contains("province")) {
            return new AppException(
                    ErrorCode.INVALID_INPUT,
                    "Database/schema dang loi o cot province. Du lieu da gui nhung khong luu duoc."
            );
        }
        if (message.contains("district")) {
            return new AppException(
                    ErrorCode.INVALID_INPUT,
                    "Database/schema dang loi o cot district. Du lieu da gui nhung khong luu duoc."
            );
        }
        if (message.contains("uniqueidentifier")
                || message.contains("conversion failed")
                || message.contains("operand type clash")
                || message.contains("uuid")) {
            return new AppException(
                    ErrorCode.INVALID_INPUT,
                    "Schema database chua khop voi User UUID. Can kiem tra kieu cot users.id va cac khoa ngoai user_id."
            );
        }

        return new AppException(
                ErrorCode.INVALID_INPUT,
                "Dang ky that bai do rang buoc du lieu hoac schema database chua khop."
        );
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (isBlank(request.getProvince())) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Vui long nhap tinh thanh");
        }

        if (isBlank(request.getDistrict())) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Vui long nhap quan huyen");
        }

        if (!Objects.equals(request.getPassword(), request.getConfirmPassword())) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Mat khau xac nhan khong khop");
        }
    }

    private void validateProfileUpdateRequest(ProfileUpdateRequest request) {
        if (isBlank(request.getProvince())) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Vui long nhap tinh thanh");
        }

        if (isBlank(request.getDistrict())) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Vui long nhap quan huyen");
        }
    }

    private void ensureCurrentUser(User currentUser) {
        if (currentUser == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
    }

    private UserResponseDTO toUserResponse(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .province(user.getProvince())
                .district(user.getDistrict())
                .identityCard(user.getIdentityCard())
                .gender(user.getGender())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String extractDeepestMessage(Throwable throwable) {
        String message = throwable.getMessage();
        Throwable current = throwable;

        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause();
        }

        return message == null ? "" : message;
    }
}
