package com.bankingeconomy.service;

import com.bankingeconomy.entity.User;
import com.bankingeconomy.utils.JwtInfo;
import com.bankingeconomy.utils.TokenPayload;
import com.nimbusds.jose.JOSEException;

import java.text.ParseException;

public interface JwtService {
    TokenPayload generateAccessToken(User user);
    TokenPayload generateRefreshToken(User user);

    boolean verifyToken(String token) throws JOSEException, ParseException;

    JwtInfo parseToken(String token) throws ParseException;
}
