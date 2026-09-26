package com.ascenddevs.safetyapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * For this project, login/password is explicitly NOT the graded security
 * mechanism (see AlertSigningService / AdaptiveRateLimiter for that) — this
 * config simply stops Spring Security's default login wall from blocking
 * every request during development. Replace with proper JWT-based auth on
 * /auth/** when the AuthController is built (see docs/API_CONTRACT.md).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
