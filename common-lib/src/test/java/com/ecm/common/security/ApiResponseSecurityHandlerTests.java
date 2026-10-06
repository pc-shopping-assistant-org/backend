package com.ecm.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseSecurityHandlerTests {

    @Test
    void unauthenticatedRequestGetsUnauthorizedEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResponseSecurityHandler.INSTANCE.commence(new MockHttpServletRequest(), response,
                new BadCredentialsException("bad"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"data\":null,\"message\":\"UNAUTHORIZED\",\"errors\":[{\"message\":\"Unauthorized\"}]}");
    }

    @Test
    void forbiddenRequestGetsForbiddenEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiResponseSecurityHandler.INSTANCE.handle(new MockHttpServletRequest(), response,
                new AccessDeniedException("no"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"message\":\"FORBIDDEN\"");
    }
}
