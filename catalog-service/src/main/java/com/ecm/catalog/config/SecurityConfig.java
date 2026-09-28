package com.ecm.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * No real authentication scheme exists yet for this backend (see AGENTS.md "chưa làm"), and
 * there is no cross-service auth propagation from the gateway either — so a service-to-service
 * Feign call (order-service reading a variant's price/stock here) carries no credentials.
 * This carves out those specific endpoints from Spring Boot's default HTTP Basic lockdown;
 * everything else keeps the default-secured behavior until real auth is implemented.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/trace-test/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/product-variants/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
