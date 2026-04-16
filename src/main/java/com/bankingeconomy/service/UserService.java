package com.bankingeconomy.service;

import com.bankingeconomy.dto.request.ProfileUpdateRequest;
import com.bankingeconomy.dto.request.RegisterRequest;
import com.bankingeconomy.dto.response.ProfileResponse;
import com.bankingeconomy.dto.response.RegisterResponse;

public interface UserService {
    RegisterResponse register(RegisterRequest request);

    ProfileResponse getProfileByEmail(String email);

    ProfileResponse updateProfile(String email, ProfileUpdateRequest request);
}
