package com.ecm.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * No real authentication scheme exists yet for this backend (see AGENTS.md "chưa làm").
 * This only carves out the throwaway /trace-test endpoints from Spring Boot's default
 * HTTP Basic lockdown so tracing can be verified end to end; everything else keeps the
 * default-secured behavior until real auth is implemented.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/trace-test/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
