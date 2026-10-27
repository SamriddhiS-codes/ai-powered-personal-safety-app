package com.ascenddevs.safetyapp.config;

import com.ascenddevs.safetyapp.security.JwtAuthFilter;
import com.ascenddevs.safetyapp.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Login security: /api/auth/** is public, every other /api call needs a valid signed token
 * (see AuthService, JwtService and TotpService). Alert signing and rate limiting remain separate
 * layers (AlertSigningService, AdaptiveRateLimiter).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // salted, deliberately slow
    }

    @Bean
    public JwtService jwtService(@Value("${app.auth.jwt-secret}") String secret) {
        return new JwtService(secret, System::currentTimeMillis);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwt) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // no cookies are used: the token is sent in a header
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(new JwtAuthFilter(jwt), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
