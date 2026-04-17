package com.bankingeconomy.exception;

import com.bankingeconomy.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Bắt AppException (lỗi nghiệp vụ)
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        Map<String, String> fieldErrors = resolveFieldErrors(ex.getMessage());
        ex.printStackTrace();
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now().toString())
                .status(errorCode.getCode())
                .path(request.getRequestURI())
                .error(ex.getMessage())
                .errors(fieldErrors.isEmpty() ? null : fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    // Bắt lỗi validation (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = Objects.requireNonNull(ex.getFieldError()).getDefaultMessage();
        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(normalizeFieldName(fieldError.getField()), fieldError.getDefaultMessage());
        }

        ex.printStackTrace();
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now().toString())
                .status(ErrorCode.INVALID_INPUT.getCode())
                .path(request.getRequestURI())
                .error(message)
                .errors(errors.isEmpty() ? null : errors)
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    // Bắt lỗi đăng nhập sai (Spring Security)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.INVALID_CREDENTIALS;
        ex.printStackTrace();
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now().toString())
                .status(errorCode.getCode())
                .path(request.getRequestURI())
                .error(errorCode.getMessage())
                .build();
        return ResponseEntity.status(401).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        String message = resolveDatabaseMessage(ex);
        Map<String, String> fieldErrors = resolveFieldErrors(message);
        ex.printStackTrace();
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now().toString())
                .status(ErrorCode.INVALID_INPUT.getCode())
                .path(request.getRequestURI())
                .error(message)
                .errors(fieldErrors.isEmpty() ? null : fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessException(
            DataAccessException ex,
            HttpServletRequest request
    ) {
        String message = resolveDatabaseMessage(ex);
        Map<String, String> fieldErrors = resolveFieldErrors(message);
        ex.printStackTrace();
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now().toString())
                .status(ErrorCode.INVALID_INPUT.getCode())
                .path(request.getRequestURI())
                .error(message)
                .errors(fieldErrors.isEmpty() ? null : fieldErrors)
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    // Bắt tất cả exception còn lại (fallback)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUncategorizedException(Exception ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;
        ex.printStackTrace();
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now().toString())
                .status(errorCode.getCode())
                .path(request.getRequestURI())
                .error(errorCode.getMessage())
                .build();
        return ResponseEntity.internalServerError().body(response);
    }

    private String resolveDatabaseMessage(Exception ex) {
        String message = ex.getMessage();
        Throwable cause = ex;

        while (cause != null) {
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                message = cause.getMessage();
            }
            cause = cause.getCause();
        }

        if (message == null) {
            return "Dữ liệu đã tồn tại hoặc không hợp lệ.";
        }

        String lower = message.toLowerCase(Locale.ROOT);

        if (lower.contains("identity_card")) {
            return "CCCD đã tồn tại";
        }
        if (lower.contains("phone")) {
            return "Số điện thoại đã tồn tại";
        }
        if (lower.contains("email")) {
            return "Email đã tồn tại";
        }
        if (lower.contains("province")) {
            return "Database/schema đang lỗi ở cột province.";
        }
        if (lower.contains("district")) {
            return "Database/schema đang lỗi ở cột district.";
        }

        return "Dữ liệu đã tồn tại hoặc không hợp lệ.";
    }

    private Map<String, String> resolveFieldErrors(String message) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (message == null || message.isBlank()) {
            return errors;
        }

        String lower = message.toLowerCase(Locale.ROOT);

        if (lower.contains("họ tên")) {
            errors.put("fullName", message);
        }
        if (lower.contains("cccd") || lower.contains("cmnd") || lower.contains("identity_card")) {
            errors.put("customerId", message);
        }
        if (lower.contains("email")) {
            errors.put("username", message);
        }
        if (lower.contains("điện thoại") || lower.contains("phone")) {
            errors.put("phone", message);
        }
        if (lower.contains("tỉnh") || lower.contains("thành") || lower.contains("province")) {
            errors.put("province", message);
        }
        if (lower.contains("quận") || lower.contains("huyện") || lower.contains("district")) {
            errors.put("district", message);
        }
        if (lower.contains("mật khẩu") || lower.contains("password")) {
            errors.put("password", message);
        }

        return errors;
    }

    private String normalizeFieldName(String fieldName) {
        return switch (fieldName) {
            case "identityCard" -> "customerId";
            case "email" -> "username";
            default -> fieldName;
        };
    }
}
