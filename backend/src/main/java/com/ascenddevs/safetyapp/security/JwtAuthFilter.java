package com.ascenddevs.safetyapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads "Authorization: Bearer <token>", checks the signature and expiry, and marks the request as
 * logged in. Only full "access" tokens count: the short-lived password-step tokens cannot be used here.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            JwtService.Claims c = jwt.verify(header.substring(7));
            if (c != null && "access".equals(c.scope())) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        c.subject(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            }
        }
        chain.doFilter(req, res);
    }
}
