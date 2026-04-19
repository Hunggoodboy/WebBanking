package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.request.AccountRequest;
import com.bankingeconomy.dto.response.AccountLookupResponse;
import com.bankingeconomy.dto.response.AccountSummaryResponse;
import com.bankingeconomy.dto.response.BalanceResponse;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.UserRepository;
import com.bankingeconomy.service.AccountService;
import com.bankingeconomy.service.BalanceCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final BalanceCacheService balanceCacheService;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────
    // Xem số dư theo Account ID (Redis cache-first)
    // ─────────────────────────────────────────
    @Override
    public BalanceResponse getBalance(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        // Kiểm tra quyền sở hữu tài khoản
        verifyOwnership(account);

        // Thử lấy từ Redis cache trước
        Double cachedBalance = balanceCacheService.getCachedBalance(accountId);

        if (cachedBalance != null) {
            log.info("Balance from CACHE for account {}: {}", account.getAccountNumber(), cachedBalance);
            return BalanceResponse.builder()
                    .accountNumber(account.getAccountNumber())
                    .balance(cachedBalance)
                    .source("CACHE")
                    .build();
        }

        // Cache miss → lấy từ DB rồi cache lại
        double dbBalance = account.getBalance();
        balanceCacheService.cacheBalance(accountId, dbBalance);

        log.info("Balance from DATABASE for account {}: {}", account.getAccountNumber(), dbBalance);
        return BalanceResponse.builder()
                .accountNumber(account.getAccountNumber())
                .balance(dbBalance)
                .source("DATABASE")
                .build();
    }

    // ─────────────────────────────────────────
    // Xem số dư theo số tài khoản
    // ─────────────────────────────────────────
    @Override
    public BalanceResponse getBalanceByAccountNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        return getBalance(account.getId());
    }

    // ─────────────────────────────────────────
    // Kiểm tra số dư đủ để chuyển tiền
    // ─────────────────────────────────────────
    @Override
    public boolean checkBalanceForTransfer(UUID accountId, double amount) {
        // Validate account tồn tại và ACTIVE
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        // Kiểm tra quyền sở hữu tài khoản
        verifyOwnership(account);

        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        }

        // Kiểm tra balance qua Redis cache
        boolean hasEnough = balanceCacheService.hasEnoughBalance(accountId, amount);

        log.info("Balance check for account {} | amount={} | sufficient={}",
                account.getAccountNumber(), amount, hasEnough);

        return hasEnough;
    }

    // ═════════════════════════════════════════
    // KIỂM TRA QUYỀN SỞ HỮU TÀI KHOẢN
    // ═════════════════════════════════════════

    /**
     * Xác minh rằng tài khoản thuộc về người dùng đang đăng nhập.
     * Lấy email từ JWT trong SecurityContext, truy vấn User,
     * so sánh user_id của Account với User hiện tại.
     *
     * @param account tài khoản cần kiểm tra quyền sở hữu
     * @throws AppException UNAUTHORIZED_ACCESS nếu không phải chủ sở hữu
     */
    private void verifyOwnership(Account account) {
        // Lấy email từ SecurityContext (JWT sub claim)
        String currentEmail = getCurrentUserEmail();
        if (currentEmail == null) {
            log.warn("Không thể xác định người dùng hiện tại từ SecurityContext");
            throw new AppException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        // Truy vấn User từ email
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // So sánh user_id của Account với User hiện tại
        if (account.getUser() == null || !account.getUser().getId().equals(currentUser.getId())) {
            log.warn("User {} (email={}) cố truy cập tài khoản {} không thuộc sở hữu",
                    currentUser.getId(), currentEmail, account.getAccountNumber());
            throw new AppException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        log.debug("Xác minh quyền sở hữu thành công: user={} → account={}",
                currentEmail, account.getAccountNumber());
    }

    /**
     * Trích xuất email của người dùng hiện tại từ JWT token trong SecurityContext.
     *
     * @return email hoặc null nếu chưa xác thực
     */
    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        // Trường hợp dùng OAuth2 JWT Resource Server
        if (principal instanceof Jwt jwt) {
            return jwt.getClaimAsString("sub");
        }

        // Trường hợp dùng UserDetails (form login)
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return userDetails.getUsername();
        }

        // Fallback: principal là String (email)
        if (principal instanceof String email) {
            return email;
        }

        return null;
    }
}

@Override
public ResponseData<?> createAccount(User user, AccountRequest request){
    String accountNumber = request != null && request.getAccountNumber() != null
            ? request.getAccountNumber().trim()
            : "";

    if (accountNumber.isBlank()) {
        return ResponseData.builder()
                .status(400)
                .message("Vui lòng nhập số tài khoản muốn đăng ký")
                .build();
    }

    if (!accountNumber.matches("\\d{6,20}")) {
        return ResponseData.builder()
                .status(400)
                .message("Số tài khoản phải gồm từ 6 đến 20 chữ số")
                .build();
    }

    if(accountRepository.findByAccountNumber(accountNumber).isPresent()) {
        return ResponseData.builder()
                .status(400)
                .message("Tài khoản đã tồn tại")
                .build();
    }
    Account account = Account.builder()
            .user(user)
            .accountNumber(accountNumber)
            .balance(0.0)
            .status(Account.AccountStatus.ACTIVE)
            .createdAt(new java.util.Date())
            .build();
    accountRepository.save(account);
    return ResponseData.builder()
            .status(201)
            .message("Account created successfully")
            .data(account.getId())
            .build();
}

@Override
public List<AccountSummaryResponse> getMyAccounts(User user) {
    List<Account> accounts = accountRepository.findAll().stream()
            .filter(account -> account.getUser() != null && account.getUser().getId().equals(user.getId()))
            .sorted(Comparator.comparing(Account::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

    return java.util.stream.IntStream.range(0, accounts.size())
            .mapToObj(index -> {
                Account account = accounts.get(index);
                boolean primary = index == 0;
                Double cachedBalance = balanceCacheService.getCachedBalance(account.getId());
                double balance = cachedBalance != null ? cachedBalance : account.getBalance();

                if (cachedBalance == null) {
                    balanceCacheService.cacheBalance(account.getId(), balance);
                }

                return AccountSummaryResponse.builder()
                        .id(account.getId())
                        .accountNumber(account.getAccountNumber())
                        .balance(balance)
                        .status(account.getStatus() != null ? account.getStatus().name() : "")
                        .name(primary ? "Tài khoản thanh toán" : "Tài khoản phụ")
                        .primary(primary)
                        .build();
            })
            .toList();
}

@Override
public AccountLookupResponse lookupByAccountNumber(String accountNumber) {
    Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

    return AccountLookupResponse.builder()
            .id(account.getId())
            .accountNumber(account.getAccountNumber())
            .accountHolderName(account.getUser() != null ? account.getUser().getFullName() : "Không rõ chủ tài khoản")
            .status(account.getStatus() != null ? account.getStatus().name() : "")
            .build();
}
}
