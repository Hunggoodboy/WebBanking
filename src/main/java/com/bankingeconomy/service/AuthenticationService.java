package com.bankingeconomy.service;


import com.bankingeconomy.dto.request.LoginRequest;
import com.bankingeconomy.dto.response.LoginResponse;

import java.text.ParseException;

public interface AuthenticationService {
    LoginResponse login(LoginRequest loginRequest);
    void logout(String token) throws ParseException;
}
