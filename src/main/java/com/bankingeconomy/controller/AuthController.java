package com.bankingeconomy.controller;


import com.bankingeconomy.dto.request.LoginRequest;
import com.bankingeconomy.dto.request.RegisterRequest;
import com.bankingeconomy.dto.response.LoginResponse;
import com.bankingeconomy.dto.response.RegisterResponse;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.repository.RedisTokenRepository;
import com.bankingeconomy.service.AuthenticationService;
import com.bankingeconomy.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.util.List;

import static org.apache.kafka.streams.kstream.EmitStrategy.log;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
@CrossOrigin(origins = "*") // Cho phép Frontend gọi API
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserService userService;


    @PostMapping("/login")
    public ResponseData<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        LoginResponse result = authenticationService.login(loginRequest);
        return new ResponseData<>(HttpStatus.OK.value(), "Đăng nhập thành công", result);
    }

    @PostMapping("/register")
    public ResponseData<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse result = userService.register(request);
        return new ResponseData<>(HttpStatus.CREATED.value(), "Đăng ký thành công", result);
    }

    @PostMapping("/register/bulk")
    public ResponseData<List<RegisterResponse>> registerBulk(
            @Valid @RequestBody List<RegisterRequest> requests) {
        List<RegisterResponse> results = userService.registerBulk(requests);
        return new ResponseData<>(HttpStatus.CREATED.value(),
                "Đăng ký " + results.size() + " tài khoản thành công", results);
    }


    @PostMapping("/logout")
    public ResponseData<?> logout(@RequestHeader("Authorization") String authHeader) throws ParseException {
        String token = authHeader.replace("Bearer ", "");
        authenticationService.logout(token);
        return new ResponseData<>(HttpStatus.OK.value(), "Đăng xuất thành công");
    }
}
