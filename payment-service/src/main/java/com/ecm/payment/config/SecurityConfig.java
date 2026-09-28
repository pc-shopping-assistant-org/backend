package com.ecm.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * No real authentication scheme exists yet for this backend (see AGENTS.md "chưa làm"), and
 * there is no cross-service auth propagation from the gateway yet either — so /payments is
 * temporarily open here rather than unreachable behind Spring Boot's default HTTP Basic
 * lockdown. Revisit once the gateway forwards a validated identity to downstream services.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/trace-test/**").permitAll()
                        .requestMatchers("/payments", "/payments/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
