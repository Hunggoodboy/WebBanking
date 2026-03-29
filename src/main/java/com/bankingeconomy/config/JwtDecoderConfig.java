package com.bankingeconomy.config;

import com.bankingeconomy.repository.RedisTokenRepository;
import com.bankingeconomy.service.JwtService;
import com.nimbusds.jose.JOSEException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;


import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Objects;


@Component
@RequiredArgsConstructor
@Slf4j
public class JwtDecoderConfig implements JwtDecoder {

    @Value("${jwt.secret-key}")
    private String secretKey;


    private final JwtService jwtService;
    private NimbusJwtDecoder nimbusJwtDecoder = null;

    @Override
    public Jwt decode(String token) throws JwtException {

        log.info("Decoding JWT token: {}", token);
        try {
            if(!jwtService.verifyToken(token)){
                throw new RuntimeException("Invalid JWT token");
            }
            if(Objects.isNull(nimbusJwtDecoder)){
                SecretKey secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HS512");
                nimbusJwtDecoder = NimbusJwtDecoder.withSecretKey(secretKeySpec)
                        .macAlgorithm(MacAlgorithm.HS512)
                        .build();
            }
        } catch (JOSEException | ParseException e) {
            throw new RuntimeException(e);
        }

        return nimbusJwtDecoder.decode(token);
    }
}
