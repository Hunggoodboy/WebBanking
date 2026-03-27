package com.bankingeconomy.service;

import com.bankingeconomy.entity.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.KeyLengthException;

public interface JwtService {
    String generateAccessToken(User user);
    String generateRefreshToken(User user);
}
