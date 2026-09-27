package com.ecm.identity.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserSummaryResponse user) {

    public AuthResponse(String accessToken, String refreshToken, long expiresIn, UserSummaryResponse user) {
        this(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
