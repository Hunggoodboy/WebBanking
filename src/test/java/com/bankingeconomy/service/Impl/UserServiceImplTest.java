package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.request.RegisterRequest;
import com.bankingeconomy.dto.response.RegisterResponse;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void registerShouldCreatePrimaryAccountAndAllowMissingConfirmPassword() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("Nguyen Van A")
                .phone("0912345678")
                .identityCard("123456789012")
                .email("test@example.com")
                .password("Password@123")
                .province("Ha Noi")
                .district("Cau Giay")
                .build();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByPhone(request.getPhone())).thenReturn(false);
        when(userRepository.existsByIdentityCard(request.getIdentityCard())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);

        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(UUID.randomUUID());
            return account;
        });

        RegisterResponse response = userService.register(request);

        assertNotNull(response.getUserId());
        assertNotNull(response.getAccountId());
        assertEquals("test@example.com", response.getEmail());
        assertNotNull(response.getAccountNumber());
        assertFalse(response.getAccountNumber().isBlank());
        assertEquals(100_000_000.0, response.getInitialBalance());

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();
        assertEquals(Account.AccountStatus.ACTIVE, savedAccount.getStatus());
        assertEquals(100_000_000.0, savedAccount.getBalance());
        assertNotNull(savedAccount.getUser());
        assertNotNull(savedAccount.getUser().getId());
    }
}
