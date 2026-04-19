package com.bankingeconomy.controller;

import com.bankingeconomy.dto.request.ProfileUpdateRequest;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.dto.response.UserResponseDTO;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseData<UserResponseDTO> getCurrentUser(@AuthenticationPrincipal User currentUser) {
        return new ResponseData<>(HttpStatus.OK.value(), "Thông tin người dùng hiện tại", userService.getCurrentUser(currentUser));
    }

    @PutMapping("/me")
    public ResponseData<UserResponseDTO> updateCurrentUser(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        return new ResponseData<>(
                HttpStatus.OK.value(),
                "Cập nhật hồ sơ thành công",
                userService.updateCurrentUser(currentUser, request)
        );
    }
}
