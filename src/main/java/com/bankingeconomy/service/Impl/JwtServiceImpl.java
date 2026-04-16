package com.bankingeconomy.service.Impl;

import com.bankingeconomy.entity.RedisToken;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.repository.RedisTokenRepository;
import com.bankingeconomy.service.JwtService;
import com.bankingeconomy.utils.JwtInfo;
import com.bankingeconomy.utils.TokenPayload;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.Token;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.text.ParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    @Value("${jwt.secret-key}")
    private String secretKey;

    private final RedisTokenRepository redisTokenRepository;

    @Override
    public TokenPayload generateAccessToken(User user) {

        // JWT header
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        // JWT payload
        Date issueTime = new Date();
        Date expriredTime = Date.from(issueTime.toInstant().plus(30, ChronoUnit.MINUTES));
        String jwtId = UUID.randomUUID().toString();

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getEmail())
                .issueTime(issueTime)
                .expirationTime(expriredTime)
                .jwtID(jwtId)
                .build();

        Payload payload = new Payload(claimsSet.toJSONObject());

        // JWT signing
        JWSObject jwsObject = new JWSObject(header, payload);
        try{
            jwsObject.sign(new MACSigner(secretKey));
        }
        catch(JOSEException e){
            throw new RuntimeException(e);
        }

        String token = jwsObject.serialize();

        return TokenPayload.builder()
                .token(token)
                .jwtId(jwtId)
                .expiredTime(expriredTime)
                .build();
    }

    @Override
    public TokenPayload generateRefreshToken(User user) {

        // JWT header
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        // JWT payload
        Date issueTime = new Date();
        Date expriredTime = Date.from(issueTime.toInstant().plus(30, ChronoUnit.DAYS));
        String jwtId = UUID.randomUUID().toString();

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getEmail())
                .issueTime(issueTime)
                .expirationTime(expriredTime)
                .jwtID(jwtId)
                .build();

        Payload payload = new Payload(claimsSet.toJSONObject());

        // JWT signing
        JWSObject jwsObject = new JWSObject(header, payload);
        try{
            jwsObject.sign(new MACSigner(secretKey));
        }
        catch(JOSEException e){
            throw new RuntimeException(e);
        }
        String token = jwsObject.serialize();

        return TokenPayload.builder()
                .token(token)
                .jwtId(jwtId)
                .expiredTime(expriredTime)
                .build();
    }

    public boolean verifyToken(String token) throws ParseException, JOSEException {

        // Verify that the token has all the necessary headers, payloads, and signatures
        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expirationDate = signedJWT.getJWTClaimsSet().getExpirationTime();
        if(expirationDate.before(new Date())){
            return false;
        }

        String jwtId = signedJWT.getJWTClaimsSet().getJWTID();
        Optional<RedisToken> byId = redisTokenRepository.findById(jwtId);
        if(byId.isPresent()){
            throw new RuntimeException("Token already exists");
        }

        return signedJWT.verify(new MACVerifier(secretKey));
    }

    public JwtInfo parseToken(String token) throws ParseException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        String jwtId = signedJWT.getJWTClaimsSet().getJWTID();
        Date issueTime = signedJWT.getJWTClaimsSet().getIssueTime();
        Date expiredTime = signedJWT.getJWTClaimsSet().getExpirationTime();

        return JwtInfo.builder()
                .jwtId(jwtId)
                .issueTime(issueTime)
                .expirationTime(expiredTime)
                .build();

    }
}
