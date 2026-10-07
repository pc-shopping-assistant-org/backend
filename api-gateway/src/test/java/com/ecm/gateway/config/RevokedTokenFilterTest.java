package com.ecm.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevokedTokenFilterTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final String KEY = "revoked-before:account:" + ACCOUNT_ID;
    private static final Instant ISSUED_AT = Instant.parse("2026-01-01T00:00:10Z");

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final RevokedTokenFilter filter = new RevokedTokenFilter(redisTemplate);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(values);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(Instant issuedAt, String accountId) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "RS256").claim("accountId", accountId);
        if (issuedAt != null) {
            builder.issuedAt(issuedAt).expiresAt(issuedAt.plusSeconds(3600));
        } else {
            builder.expiresAt(Instant.parse("2026-01-02T00:00:00Z"));
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build()));
    }

    private MockHttpServletResponse response = new MockHttpServletResponse();

    private void run() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), response, chain);
    }

    private void assertForwarded() throws Exception {
        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    private void assertRejected() throws Exception {
        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void keepsATokenWhenNothingWasRevoked() throws Exception {
        authenticate(ISSUED_AT, ACCOUNT_ID.toString());
        when(values.get(KEY)).thenReturn(null);

        run();

        assertForwarded();
    }

    @Test
    void rejectsATokenIssuedBeforeTheMarker() throws Exception {
        authenticate(ISSUED_AT, ACCOUNT_ID.toString());
        when(values.get(KEY)).thenReturn(String.valueOf(ISSUED_AT.getEpochSecond() + 1));

        run();

        assertRejected();
    }

    @Test
    void keepsATokenIssuedAtOrAfterTheMarker() throws Exception {
        authenticate(ISSUED_AT, ACCOUNT_ID.toString());
        when(values.get(KEY)).thenReturn(String.valueOf(ISSUED_AT.getEpochSecond()));

        run();

        assertForwarded();
    }

    @Test
    void rejectsTheTokenWhenTheRevocationStoreIsDown() throws Exception {
        authenticate(ISSUED_AT, ACCOUNT_ID.toString());
        when(values.get(KEY)).thenThrow(new RedisConnectionFailureException("down"));

        run();

        assertRejected();
    }

    @Test
    void leavesATokenWithoutAccountIdAlone() throws Exception {
        authenticate(ISSUED_AT, null);

        run();

        assertForwarded();
    }

    @Test
    void ignoresRequestsWithoutAuthentication() throws Exception {
        run();

        assertForwarded();
    }
}
