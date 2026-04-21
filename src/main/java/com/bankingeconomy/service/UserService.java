package com.bankingeconomy.service;

import com.bankingeconomy.dto.request.ProfileUpdateRequest;
import com.bankingeconomy.dto.request.RegisterRequest;
import com.bankingeconomy.dto.response.RegisterResponse;
import com.bankingeconomy.dto.response.UserResponseDTO;
import com.bankingeconomy.entity.User;

import java.util.List;

public interface UserService {
    RegisterResponse register(RegisterRequest request);
    UserResponseDTO getCurrentUser(User currentUser);
    UserResponseDTO updateCurrentUser(User currentUser, ProfileUpdateRequest request);
    List<RegisterResponse> registerBulk(List<RegisterRequest> requests);
}
