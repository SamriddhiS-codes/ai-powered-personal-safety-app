package com.ascenddevs.safetyapp.controller;

import com.ascenddevs.safetyapp.service.AuthService;
import org.springframework.web.bind.annotation.*;

/** Public endpoints: everything else under /api needs a valid session token. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterRequest(String name, String email, String password) {
    }

    public record LoginRequest(String email, String password) {
    }

    /** token is the setup or mfa token from the previous step; code is the 6-digit authenticator code. */
    public record CodeRequest(String token, String code) {
    }

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public AuthService.SetupInfo register(@RequestBody RegisterRequest r) {
        return auth.register(r.name(), r.email(), r.password());
    }

    @PostMapping("/register/confirm")
    public AuthService.Session confirm(@RequestBody CodeRequest r) {
        return auth.confirmSetup(r.token(), r.code());
    }

    @PostMapping("/login")
    public AuthService.LoginStep login(@RequestBody LoginRequest r) {
        return auth.login(r.email(), r.password());
    }

    @PostMapping("/login/verify")
    public AuthService.Session verify(@RequestBody CodeRequest r) {
        return auth.loginVerify(r.token(), r.code());
    }
}
