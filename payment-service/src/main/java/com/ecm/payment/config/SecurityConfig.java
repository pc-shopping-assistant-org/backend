package com.ecm.payment.config;

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
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        // Settling a payment by hand is the shop's tool: anyone else could mark an order paid without paying
                        .requestMatchers(HttpMethod.POST, "/payments/*/webhook").hasRole("EMPLOYEE")
                        // VNPAY calls back and redirects the customer here; the signature of the request is what is checked
                        .requestMatchers(HttpMethod.GET, "/payments/vnpay/ipn", "/payments/vnpay/return").permitAll()
                        // Internal saga call from order-service (Feign sends no token); the gateway still requires auth.
                        .requestMatchers(HttpMethod.POST, "/payments").permitAll()
                        .requestMatchers("/payments/admin/**", "/payment-methods/admin").hasRole("EMPLOYEE")
                        .anyRequest().authenticated())
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
