package com.ecm.order.config;

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
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/trace-test/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/order-items/variants/*/exists").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/order-items/**").authenticated()
                        .requestMatchers("/orders/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/orders").hasRole("CUSTOMER")
                        .requestMatchers("/orders/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(ApiResponseSecurityHandler.INSTANCE)
                        .accessDeniedHandler(ApiResponseSecurityHandler.INSTANCE))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(ApiResponseSecurityHandler.INSTANCE)
                        .accessDeniedHandler(ApiResponseSecurityHandler.INSTANCE)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
