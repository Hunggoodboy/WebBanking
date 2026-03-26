package com.bankingeconomy.service;


import com.bankingeconomy.dto.request.LoginRequest;
import com.bankingeconomy.dto.response.LoginResponse;

public interface AuthenticationService {
    LoginResponse login(LoginRequest loginRequest);
}
