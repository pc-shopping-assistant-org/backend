package com.ecm.gateway.config;

import com.ecm.common.security.ApiResponseSecurityHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter converter,
                                    RevokedTokenFilter revokedTokenFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/.well-known/jwks.json",
                                "/identity-service/auth/**", "/identity-service/.well-known/jwks.json").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/**", "/categories/**", "/brands/**", "/product-variants/**",
                                "/catalog-service/products/**", "/catalog-service/categories/**", "/catalog-service/brands/**",
                                "/catalog-service/product-variants/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/search", "/search-service/products/search").permitAll()
                        .requestMatchers(HttpMethod.POST, "/payments/*/webhook", "/payment-service/payments/*/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .addFilterAfter(revokedTokenFilter, BearerTokenAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(ApiResponseSecurityHandler.INSTANCE)
                        .accessDeniedHandler(ApiResponseSecurityHandler.INSTANCE))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(ApiResponseSecurityHandler.INSTANCE)
                        .accessDeniedHandler(ApiResponseSecurityHandler.INSTANCE)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
