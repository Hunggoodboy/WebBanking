package com.bankingeconomy.service.Impl;


import com.bankingeconomy.dto.request.LoginRequest;
import com.bankingeconomy.dto.response.LoginResponse;
import com.bankingeconomy.entity.RedisToken;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.repository.RedisTokenRepository;
import com.bankingeconomy.service.AuthenticationService;
import com.bankingeconomy.service.JwtService;
import com.bankingeconomy.utils.JwtInfo;
import com.bankingeconomy.utils.TokenPayload;
import com.nimbusds.jose.JOSEException;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Date;


@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {


    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RedisTokenRepository redisTokenRepository;


    public LoginResponse login(LoginRequest request) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword());
        Authentication authenticate = authenticationManager.authenticate(authenticationToken);

        User user = (User) authenticate.getPrincipal();

        TokenPayload accessPayload = jwtService.generateAccessToken(user);
        TokenPayload refreshPayload = jwtService.generateRefreshToken(user);
        redisTokenRepository.save(RedisToken.builder()
                        .jwtID(refreshPayload.getJwtId())
                        .expiredTime(refreshPayload.getExpiredTime().getTime())
                .build());
        // tra ve token
        return LoginResponse.builder()
                .accessToken(accessPayload.getToken())
                .refreshToken(refreshPayload.getToken())
                        .build();
    }

    public void logout(String token) throws ParseException {
        JwtInfo jwtInfo = jwtService.parseToken(token);
        String jwtId = jwtInfo.getJwtId();
        Date issueTime = jwtInfo.getIssueTime();
        Date expiredTime = jwtInfo.getExpirationTime();

        if(expiredTime.before(new Date())){
            return;
        }

        RedisToken redisToken = RedisToken.builder()
                .jwtID(jwtId)
                .expiredTime(expiredTime.getTime() - new Date().getTime())
                .build();

        redisTokenRepository.save(redisToken);
        log.info("Logout successful");
    }
}
