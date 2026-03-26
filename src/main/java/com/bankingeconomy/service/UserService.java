package com.bankingeconomy.service;

import com.bankingeconomy.dto.request.RegisterRequest;
import com.bankingeconomy.dto.response.RegisterResponse;

public interface UserService {
    RegisterResponse register(RegisterRequest request);
}
