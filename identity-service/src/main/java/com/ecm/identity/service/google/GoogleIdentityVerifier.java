package com.ecm.identity.service.google;

import com.ecm.common.exception.BusinessException;
import com.ecm.common.exception.ExternalServiceException;
import com.ecm.identity.config.GoogleProperties;
import com.ecm.identity.exception.IdentityErrorCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Verifies Google Identity Services ID tokens on the server boundary.
 * The browser-provided token is never treated as an account identifier until signature,
 * issuer, audience and email verification have succeeded. The stable Google {@code sub}
 * is returned alongside the normalized email so the service can link by provider identity
 * and use email only for the first link.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleIdentityVerifier {

    private static final Set<String> GOOGLE_ISSUERS = Set.of(
            "accounts.google.com",
            "https://accounts.google.com"
    );

    private final GoogleProperties properties;
    private volatile GoogleIdTokenVerifier verifier;

    public GoogleIdentity verify(String rawIdToken) {
        if (!StringUtils.hasText(rawIdToken)) {
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS, "ID token is required");
        }

        String clientId = properties.getClientId();
        if (!StringUtils.hasText(clientId)) {
            log.warn("Google login was requested without google.client-id configured");
            throw new ExternalServiceException("Google", "Google authentication is not configured");
        }

        try {
            // 1. Verify signature, issuer, audience and expiration
            GoogleIdToken token = getVerifier(clientId).verify(rawIdToken.trim());
            if (token == null) {
                throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS, "Invalid Google ID token");
            }

            // 2. Extract and validate payload claims
            GoogleIdToken.Payload payload = token.getPayload();
            if (payload == null
                    || !GOOGLE_ISSUERS.contains(payload.getIssuer())
                    || !StringUtils.hasText(payload.getSubject())
                    || !StringUtils.hasText(payload.getEmail())
                    || !Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS, "Invalid or unverified Google account");
            }

            // 3. Return normalized identity
            return new GoogleIdentity(
                    payload.getSubject().trim(),
                    payload.getEmail().trim().toLowerCase(Locale.ROOT)
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException ex) {
            log.warn("Google ID-token verification failed: {}", ex.getMessage());
            throw new BusinessException(IdentityErrorCode.INVALID_CREDENTIALS, "Google authentication failed");
        }
    }

    private GoogleIdTokenVerifier getVerifier(String clientId) throws GeneralSecurityException, java.io.IOException {
        GoogleIdTokenVerifier current = verifier;
        if (current == null) {
            synchronized (this) {
                current = verifier;
                if (current == null) {
                    current = new GoogleIdTokenVerifier.Builder(
                            GoogleNetHttpTransport.newTrustedTransport(),
                            GsonFactory.getDefaultInstance()
                    )
                            .setAudience(List.of(clientId))
                            .build();
                    verifier = current;
                }
            }
        }
        return current;
    }
}
