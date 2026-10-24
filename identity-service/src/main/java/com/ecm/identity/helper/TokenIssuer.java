package com.ecm.identity.helper;

import com.ecm.common.exception.BusinessException;
import com.ecm.identity.config.JwtProperties;
import com.ecm.identity.config.JwtTokenProvider;
import com.ecm.identity.dto.response.AuthResponse;
import com.ecm.identity.dto.response.UserSummaryResponse;
import com.ecm.identity.entity.Account;
import com.ecm.identity.entity.Role;
import com.ecm.identity.exception.IdentityErrorCode;
import com.ecm.identity.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Issues the access/refresh token pair and the sign-in response that carries it. */
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private static final int MILLIS_IN_SECOND = 1000;

    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final RoleRepository roleRepository;
    private final UserSummaryResolver userSummaryResolver;

    /** Resolves the role and profile of the account, then issues a fresh pair. */
    public AuthResponse issueFor(Account account) {
        Role role = roleRepository.findById(account.getRoleId())
                .orElseThrow(() -> new BusinessException(IdentityErrorCode.ROLE_NOT_CONFIGURED));
        return issue(account, role, userSummaryResolver.resolve(account, role));
    }

    public AuthResponse issue(Account account, Role role, UserSummaryResponse userSummary) {
        String accessToken = tokenProvider.generateAccessToken(
                account.getId(), account.getEmail(), role.getName(), account.getStatus().name());
        String refreshToken = tokenProvider.generateRefreshToken(account.getId(), account.getEmail());

        return new AuthResponse(
                accessToken, refreshToken, jwtProperties.getAccessTokenExpirationMs() / MILLIS_IN_SECOND, userSummary);
    }
}
