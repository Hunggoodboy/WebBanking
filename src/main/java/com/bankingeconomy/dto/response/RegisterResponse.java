package com.bankingeconomy.dto.response;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegisterResponse {

    private String email;
}
