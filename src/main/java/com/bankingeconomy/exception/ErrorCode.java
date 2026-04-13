package com.bankingeconomy.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    UNCATEGORIZED_EXCEPTION(9999, "Lỗi hệ thống"),
    USER_EXISTED(1001, "Email đã tồn tại"),
    INVALID_CREDENTIALS(1002, "Thông tin đăng nhập không đúng"),
    INVALID_INPUT(1003, "Dữ liệu đầu vào không hợp lệ"),
    ;

    private final int code;
    private final String message;
}
