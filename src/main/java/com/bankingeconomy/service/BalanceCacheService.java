package com.bankingeconomy.service;

import java.util.UUID;

public interface BalanceCacheService {

    /**
     * Cache số dư vào Redis với TTL 5 phút
     */
    void cacheBalance(UUID accountId, double balance);

    /**
     * Lấy số dư từ Redis cache.
     * Trả về null nếu cache miss.
     */
    Double getCachedBalance(UUID accountId);

    /**
     * Xóa cache balance (gọi sau khi cập nhật balance)
     */
    void evictBalance(UUID accountId);

    /**
     * Kiểm tra số dư có đủ để chuyển tiền không.
     * Ưu tiên lấy từ cache, fallback sang DB.
     */
    boolean hasEnoughBalance(UUID accountId, double amount);
}
