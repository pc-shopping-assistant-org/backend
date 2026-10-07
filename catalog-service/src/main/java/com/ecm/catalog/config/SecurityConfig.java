package com.ecm.catalog.config;

import com.ecm.common.security.ApiResponseSecurityHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/product-variants/stock-summary").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/categories/admin", "/brands/admin", "/products/admin", "/products/admin/**").hasRole("EMPLOYEE")
                        .requestMatchers(HttpMethod.GET, "/products/**", "/categories/**", "/brands/**", "/product-variants/**", "/cart-variant-details").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/products/*/reviews").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.PATCH, "/products/*/reviews/*").hasRole("CUSTOMER")
                        .anyRequest().hasRole("EMPLOYEE"))
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
