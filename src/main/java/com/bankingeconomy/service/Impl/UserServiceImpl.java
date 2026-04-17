package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.request.RegisterRequest;
import com.bankingeconomy.dto.response.RegisterResponse;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.enums.Role;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.repository.UserRepository;
import com.bankingeconomy.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public RegisterResponse register(RegisterRequest request) {
        validateRegisterRequest(request);

        if(userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }
        if(userRepository.existsByPhone(request.getPhone())) {
            throw new AppException(ErrorCode.PHONE_EXISTED);
        }
        if(userRepository.existsByIdentityCard(request.getIdentityCard())) {
            throw new AppException(ErrorCode.IDENTITY_CARD_EXISTED);
        }

        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .province(request.getProvince())
                .district(request.getDistrict())
                .phone(request.getPhone())
                .identityCard(request.getIdentityCard())
                .role(User.Role.valueOf("CUSTOMER"))
                .build();

        try {
            userRepository.saveAndFlush(user);
        } catch (RuntimeException ex) {
            throw resolveRegisterException(ex);
        }

        return RegisterResponse.builder()
                .email(user.getEmail())
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
        if (message.contains("province")) {
            return new AppException(ErrorCode.INVALID_INPUT, "Database/schema đang lỗi ở cột province. Dữ liệu tỉnh/thành đã được gửi nhưng không lưu được.");
        }
        if (message.contains("district")) {
            return new AppException(ErrorCode.INVALID_INPUT, "Database/schema đang lỗi ở cột district. Dữ liệu quận/huyện đã được gửi nhưng không lưu được.");
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
            throw new AppException(ErrorCode.INVALID_INPUT, "Vui lòng nhập tỉnh thành");
        }

        if (isBlank(request.getDistrict())) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Vui lòng nhập quận huyện");
        }

        if (!Objects.equals(request.getPassword(), request.getConfirmPassword())) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Mật khẩu xác nhận không khớp");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
