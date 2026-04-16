package com.bankingeconomy.controller;

import com.bankingeconomy.dto.request.ProfileUpdateRequest;
import com.bankingeconomy.dto.response.ProfileResponse;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseData<ProfileResponse> getCurrentProfile(@AuthenticationPrincipal Jwt jwt) {
        ProfileResponse profile = userService.getProfileByEmail(resolveEmail(jwt));
        return new ResponseData<>(HttpStatus.OK.value(), "Lay thong tin ca nhan thanh cong", profile);
    }

    @PutMapping("/me")
    public ResponseData<ProfileResponse> updateCurrentProfile(@RequestBody ProfileUpdateRequest request,
                                                             @AuthenticationPrincipal Jwt jwt) {
        ProfileResponse profile = userService.updateProfile(resolveEmail(jwt), request);
        return new ResponseData<>(HttpStatus.OK.value(), "Cap nhat thong tin ca nhan thanh cong", profile);
    }

    private String resolveEmail(Jwt jwt) {
        return jwt != null && StringUtils.hasText(jwt.getSubject()) ? jwt.getSubject() : null;
    }
}
