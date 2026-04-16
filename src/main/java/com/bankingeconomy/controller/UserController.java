package com.bankingeconomy.controller;

import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.dto.response.UserResponseDTO;
import com.bankingeconomy.entity.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @GetMapping("/me")
    public ResponseData<?> getCurrentUser(@AuthenticationPrincipal User currentUser) {
        UserResponseDTO dto = UserResponseDTO.builder()
                .id(currentUser.getId())
                .email(currentUser.getEmail())
                .fullName(currentUser.getFullName())
                .phone(currentUser.getPhone())
                .province(currentUser.getProvince())
                .district(currentUser.getDistrict())
                .role(currentUser.getRole().name())
                .build();

        return new ResponseData<>(200, "Thông tin người dùng hiện tại", dto);
    }
}
