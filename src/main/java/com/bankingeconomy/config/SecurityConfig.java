package com.bankingeconomy.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.bankingeconomy.service.Impl.UserDetailServiceCustomizer;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {


    private final UserDetailServiceCustomizer userDetailsService;
    private final JwtDecoderConfig jwtDecoderConfig;
    private final CustomJwtAuthenticationConverter customJwtAuthenticationConverter;

    private final String[] WHITE_LIST = {
            "/api/auth/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/js/**",
            "/css/**",
            "/images/**",
            "/historyTransfer",
            "/historyTransfer.css",
            "/historyTransfer.js",
            "/webjars/**",
            "/favicon.ico",
            "/login",
            "/api/mock/**", "/api/hadoop/**",
            "/register",
            "/dashboard",
            "/admin/dashboard",
            "/transfer",
            "/beneficiaries",
            "/profile",
            "/api/test-kafka/**",
            "/api/user/**"
            ,"/",
            "/my-balance"
            ,"/statistics",
            "/admin/quarterly-growth"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(WHITE_LIST).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoderConfig)
                                .jwtAuthenticationConverter(customJwtAuthenticationConverter)

                        )
                );
        return http.build();
    }


    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(authenticationProvider);
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
