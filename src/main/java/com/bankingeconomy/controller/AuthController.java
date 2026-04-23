package com.bankingeconomy.controller;

import com.bankingeconomy.dto.request.LoginRequest;
import com.bankingeconomy.dto.request.RegisterRequest;
import com.bankingeconomy.dto.response.LoginResponse;
import com.bankingeconomy.dto.response.RegisterResponse;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.service.AuthenticationService;
import com.bankingeconomy.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserService userService;

    @PostMapping("/login")
    public ResponseData<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        LoginResponse result = authenticationService.login(loginRequest);
        return new ResponseData<>(HttpStatus.OK.value(), "Dang nhap thanh cong", result);
    }

    @PostMapping("/register")
    public ResponseData<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse result = userService.register(request);
        return new ResponseData<>(HttpStatus.CREATED.value(), "Dang ky thanh cong", result);
    }

    @PostMapping({"/register/bulk", "/bulkregister", "/buckregister"})
    public ResponseData<List<RegisterResponse>> registerBulk(
            @RequestBody List<@Valid RegisterRequest> requests) {
        List<RegisterResponse> results = userService.registerBulk(requests);
        return new ResponseData<>(
                HttpStatus.CREATED.value(),
                "Dang ky " + results.size() + " tai khoan thanh cong",
                results
        );
    }

    @PostMapping("/logout")
    public ResponseData<?> logout(@RequestHeader("Authorization") String authHeader) throws ParseException {
        String token = authHeader.replace("Bearer ", "");
        authenticationService.logout(token);
        return new ResponseData<>(HttpStatus.OK.value(), "Dang xuat thanh cong");
    }
}
