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
    ACCOUNT_NOT_FOUND(2001, "Tài khoản không tồn tại"),
    USER_NOT_FOUND(2002, "Người dùng không tồn tại"),
    INSUFFICIENT_BALANCE(2003, "Số dư không đủ"),
    ACCOUNT_INACTIVE(2004, "Tài khoản không hoạt động"),
    UNAUTHORIZED_ACCESS(4003, "Bạn không có quyền truy cập tài khoản này"),
    ;

    private final int code;
    private final String message;
}

