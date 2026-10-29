package com.ecm.identity.service;

import com.ecm.common.exception.BusinessException;
import com.ecm.identity.config.JwtProperties;
import com.ecm.identity.config.JwtTokenProvider;
import com.ecm.identity.dto.response.AuthResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.helper.TokenIssuer;
import com.ecm.identity.repository.AccountRepository;
import com.ecm.identity.util.AccountChecks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** The life of a signed-in session: renewing the tokens and ending it with a logout: UC-AUTH-001, 004. */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final AccountRepository accountRepository;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final TokenRevocationService tokenRevocationService;
    private final TokenIssuer tokenIssuer;

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        // 1. Only a valid, unexpired refresh token is accepted; an access token is not
        if (!tokenProvider.validateToken(refreshToken) || !tokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        // 2. A logout (or lock) revokes every token issued before it, refresh tokens included
        UUID accountId = tokenProvider.getAccountId(refreshToken);
        Instant issuedAt = tokenProvider.getIssuedAt(refreshToken);
        if (accountId == null || issuedAt == null || tokenRevocationService.isRevoked(accountId, issuedAt)) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        // 3. The account has to be usable still; its role may have changed since the last token
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS));
        AccountChecks.ensureUsable(account);
        return tokenIssuer.issueFor(account);
    }

    public boolean logout(String accessToken) {
        // 1. Reject malformed, expired, or refresh tokens
        if (!tokenProvider.validateToken(accessToken) || tokenProvider.isRefreshToken(accessToken)) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        // 2. Determine the account to revoke tokens for
        UUID accountId = tokenProvider.getAccountId(accessToken);
        if (accountId == null) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        // 3. Revoke every token issued before this logout instant, for the refresh lifetime.
        // The +1s margin covers the token being revoked itself, whose iat is truncated to the
        // current second and would otherwise compare equal to the cutoff instead of before it.
        tokenRevocationService.revokeBefore(accountId, Instant.now().plusSeconds(1),
                Duration.ofMillis(jwtProperties.getRefreshTokenExpirationMs()));
        return true;
    }
}
