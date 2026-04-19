package com.bankingeconomy.service.Impl;

import com.bankingeconomy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDetailServiceCustomizer implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        System.out.println("Đang tìm theo Email: " + email);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Email không tồn tại: " + email));
    }

    public UserDetails loadUserById(UUID id) {
        System.out.println("Đang tìm theo ID: " + id);
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("ID không tồn tại: " + id));
    }
}
