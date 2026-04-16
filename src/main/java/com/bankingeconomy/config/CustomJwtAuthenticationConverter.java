package com.bankingeconomy.config;

import com.bankingeconomy.service.Impl.UserDetailServiceCustomizer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter; // Chọn đúng thư viện này!
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserDetailServiceCustomizer userDetailsService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String userId = jwt.getSubject();

        UserDetails userDetails = userDetailsService.loadUserById(UUID.fromString(userId));

        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }
}