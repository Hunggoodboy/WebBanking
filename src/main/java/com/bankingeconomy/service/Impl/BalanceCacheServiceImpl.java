package com.bankingeconomy.service.Impl;

import com.bankingeconomy.entity.Account;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.service.BalanceCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceCacheServiceImpl implements BalanceCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AccountRepository accountRepository;

    private static final String BALANCE_KEY_PREFIX = "account:balance:";
    private static final long BALANCE_TTL_MINUTES = 5;

    private String buildKey(UUID accountId) {
        return BALANCE_KEY_PREFIX + accountId.toString();
    }

    @Override
    public void cacheBalance(UUID accountId, double balance) {
        String key = buildKey(accountId);
        redisTemplate.opsForValue().set(key, balance, BALANCE_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("Cached balance for account {}: {}", accountId, balance);
    }

    @Override
    public Double getCachedBalance(UUID accountId) {
        String key = buildKey(accountId);
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            log.debug("Cache HIT for account {}", accountId);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        }
        log.debug("Cache MISS for account {}", accountId);
        return null;
    }

    @Override
    public void evictBalance(UUID accountId) {
        String key = buildKey(accountId);
        redisTemplate.delete(key);
        log.debug("Evicted balance cache for account {}", accountId);
    }

    @Override
    public boolean hasEnoughBalance(UUID accountId, double amount) {
        // Ưu tiên lấy từ cache
        Double cachedBalance = getCachedBalance(accountId);
        if (cachedBalance != null) {
            return cachedBalance >= amount;
        }

        // Fallback: query DB
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        // Cache lại cho lần sau
        cacheBalance(accountId, account.getBalance());

        return account.getBalance() >= amount;
    }

    // ─────────────────────────────────────────
    // Cập nhật số dư → DB + Redis Cache đồng thời
    // ─────────────────────────────────────────
    @Override
    @Transactional
    public void updateBalance(UUID accountId, double newBalance) {
        // 1. Cập nhật số dư trong DB
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        account.setBalance(newBalance);
        accountRepository.save(account);

        // 2. Cập nhật luôn vào Redis cache (Cache Update thay vì Cache Evict)
        //    → đảm bảo lần đọc tiếp theo lấy đúng số dư mới nhất
        cacheBalance(accountId, newBalance);

        log.info("Đã cập nhật số dư tài khoản {} = {} (DB + Redis Cache)",
                account.getAccountNumber(), newBalance);
    }
}
