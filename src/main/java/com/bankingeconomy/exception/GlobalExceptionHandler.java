package com.bankingeconomy.exception;

import com.bankingeconomy.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Bắt AppException (lỗi nghiệp vụ)
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now().toString())
                .status(errorCode.getCode())
                .path(request.getRequestURI())
                .error(errorCode.getMessage())
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    // Bắt lỗi validation (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = Objects.requireNonNull(ex.getFieldError()).getDefaultMessage();
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now().toString())
                .status(ErrorCode.INVALID_INPUT.getCode())
                .path(request.getRequestURI())
                .error(message)
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    // Bắt lỗi đăng nhập sai (Spring Security)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.INVALID_CREDENTIALS;
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now().toString())
                .status(errorCode.getCode())
                .path(request.getRequestURI())
                .error(errorCode.getMessage())
                .build();
        return ResponseEntity.status(401).body(response);
    }

    // Bắt tất cả exception còn lại (fallback)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUncategorizedException(Exception ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now().toString())
                .status(errorCode.getCode())
                .path(request.getRequestURI())
                .error(errorCode.getMessage())
                .build();
        return ResponseEntity.internalServerError().body(response);
    }
}
