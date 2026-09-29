package com.ecm.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * ID token returned by Google Identity Services in the browser.
 */
public record GoogleLoginRequest(

        @NotBlank(message = "Google ID token is required")
        String idToken) {
}
