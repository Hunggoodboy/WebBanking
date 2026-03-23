package com.bankingeconomy.service.Impl;


import com.bankingeconomy.dto.request.LoginRequest;
import com.bankingeconomy.dto.response.LoginResponse;
import com.bankingeconomy.model.User;
import com.bankingeconomy.service.AuthenticationService;
import com.bankingeconomy.service.JwtService;
import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {


    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;


    public LoginResponse login(LoginRequest request) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword());
        Authentication authenticate = authenticationManager.authenticate(authenticationToken);

        User user = (User) authenticate.getPrincipal();

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        // tra ve token
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                        .build();


    }
}
