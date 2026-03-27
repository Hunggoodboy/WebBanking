package com.bankingeconomy.service.Impl;

import com.bankingeconomy.entity.User;
import com.bankingeconomy.service.JwtService;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.stereotype.Service;


import java.time.temporal.ChronoUnit;
import java.util.Date;


@Service
public class JwtServiceImpl implements JwtService {

    private String secretKey = "eyJhbGciOiJIUzUxMiJ9.ew0KICAic3ViIjogIjEyMzQ1Njc4OTAiLA0KICAibmFtZSI6ICJBbmlzaCBOYXRoIiwNCiAgImlhdCI6IDE1MTYyMzkwMjINCn0.ZjuawnJ2EoXlKYbNRffaOHKQikY7wPWzIYCePxB7512jl2EjIUg2Pz_ShjVBssd34eOEJ9n9gstU9D3oVRdL8";

    @Override
    public String generateAccessToken(User user) {

        // JWT header
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        // JWT payload
        Date issueTime = new Date();
        Date expriredTime = Date.from(issueTime.toInstant().plus(30, ChronoUnit.MINUTES));

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getEmail())
                .issueTime(issueTime)
                .expirationTime(expriredTime)
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

        return jwsObject.serialize();
    }

    @Override
    public String generateRefreshToken(User user) {

        // JWT header
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        // JWT payload
        Date issueTime = new Date();
        Date expriredTime = Date.from(issueTime.toInstant().plus(30, ChronoUnit.DAYS));

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getEmail())
                .issueTime(issueTime)
                .expirationTime(expriredTime)
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
        return jwsObject.serialize();
    }
}
