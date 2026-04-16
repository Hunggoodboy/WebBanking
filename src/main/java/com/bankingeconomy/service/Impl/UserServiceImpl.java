package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.request.ProfileUpdateRequest;
import com.bankingeconomy.dto.request.RegisterRequest;
import com.bankingeconomy.dto.response.AccountSummaryResponse;
import com.bankingeconomy.dto.response.ProfileResponse;
import com.bankingeconomy.dto.response.RegisterResponse;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.enums.Role;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.UserRepository;
import com.bankingeconomy.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    @Override
    public RegisterResponse register(RegisterRequest request) {
        if(userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .identityCard(request.getIdentityCard())
                .role(Role.User)
                .build();

        userRepository.save(user);

        return RegisterResponse.builder()
                .email(user.getEmail())
                .build();

    }

    @Override
    public ProfileResponse getProfileByEmail(String email) {
        return toProfileResponse(getUserByEmail(email));
    }

    @Override
    public ProfileResponse updateProfile(String email, ProfileUpdateRequest request) {
        User currentUser = getUserByEmail(email);

        if (!StringUtils.hasText(request.getFullName())
                || !StringUtils.hasText(request.getEmail())
                || !StringUtils.hasText(request.getPhone())
                || !StringUtils.hasText(request.getIdentityCard())) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        String normalizedEmail = request.getEmail().trim();
        String normalizedIdentityCard = request.getIdentityCard().trim();

        if (userRepository.existsByEmailAndIdNot(normalizedEmail, currentUser.getId())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        if (userRepository.existsByIdentityCardAndIdNot(normalizedIdentityCard, currentUser.getId())) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        currentUser.setFullName(request.getFullName().trim());
        currentUser.setEmail(normalizedEmail);
        currentUser.setPhone(request.getPhone().trim());
        currentUser.setIdentityCard(normalizedIdentityCard);
        currentUser.setGender(StringUtils.hasText(request.getGender()) ? request.getGender().trim() : null);

        userRepository.save(currentUser);
        return toProfileResponse(currentUser);
    }

    private User getUserByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private ProfileResponse toProfileResponse(User user) {
        List<AccountSummaryResponse> accounts = accountRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toAccountSummary)
                .toList();

        return ProfileResponse.builder()
                .userId(String.valueOf(user.getId()))
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .identityCard(user.getIdentityCard())
                .gender(user.getGender())
                .role(user.getRole() != null ? user.getRole().name() : Role.User.name())
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().format(DATE_TIME_FORMATTER) : null)
                .accounts(accounts)
                .build();
    }

    private AccountSummaryResponse toAccountSummary(Account account) {
        return AccountSummaryResponse.builder()
                .id(account.getId() != null ? account.getId().toString() : null)
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .status(account.getStatus() != null ? account.getStatus().name() : null)
                .createdAt(account.getCreatedAt() != null ? account.getCreatedAt().toInstant().toString() : null)
                .build();
    }
}
