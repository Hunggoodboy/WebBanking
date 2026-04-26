package com.bankingeconomy.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseData<T>  {
       private int status;
    private String message;
    private T data;

    // ================= STATIC METHODS =================

    public static <T> ResponseData<T> success(T data) {
        return ResponseData.<T>builder()
                .status(200)
                .message("Success")
                .data(data)
                .build();
    }

    public static <T> ResponseData<T> success(String message, T data) {
        return ResponseData.<T>builder()
                .status(200)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ResponseData<T> error(int status, String message) {
        return ResponseData.<T>builder()
                .status(status)
                .message(message)
                .build();
    }

    // Constructor cho PUT, DELETE
    public ResponseData(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
