package com.ascenddevs.safetyapp.service;

import com.ascenddevs.safetyapp.entity.User;
import com.ascenddevs.safetyapp.repository.UserRepository;
import com.ascenddevs.safetyapp.security.JwtService;
import com.ascenddevs.safetyapp.security.TotpService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Sign-up and login with three layers:
 *   1. Password, stored only as a salted bcrypt hash.
 *   2. A one-time code (TOTP) from the user's authenticator app.
 *   3. A signed token (JWT, HS256) that proves the login to every later request.
 * Plus brute-force protection: repeated failures lock the account for 15 minutes.
 */
@Service
public class AuthService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCK_TIME = Duration.ofMinutes(15);
    static final long ACCESS_TTL_SEC = 60 * 60;
    static final long STEP_TTL_SEC = 5 * 60;
    static final String ISSUER = "Safety App";

    public record SetupInfo(String status, String userId, String setupToken, String setupKey, String otpauthUri) {
    }

    public record LoginStep(String status, String mfaToken, SetupInfo setup) {
    }

    public record Session(String token, String userId, String name, long expiresAtMs) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final SecureRandom random = new SecureRandom();
    // Used to spend the same time on a login for an unknown email, so response time does not reveal which emails exist.
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("not-a-real-password-1");
    }

    public SetupInfo register(String name, String email, String password) {
        String error = AuthValidator.validateSignUp(name, email, password);
        if (error != null) throw bad(error);
        String mail = AuthValidator.normalizeEmail(email);
        if (users.findByEmail(mail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        byte[] key = new byte[32];
        random.nextBytes(key);
        User user = new User(name.trim(), mail, encoder.encode(password), Base64.getEncoder().encodeToString(key));
        user.setTotpSecret(TotpService.newSecret());
        return setupInfo(users.save(user), "SETUP_REQUIRED");
    }

    /** Finishes enrolling the authenticator app: the first valid code proves the user really has it. */
    public Session confirmSetup(String setupToken, String code) {
        User user = userFromToken(setupToken, "setup");
        assertNotLocked(user);
        acceptCode(user, code);
        user.setTotpEnabled(true);
        return session(users.save(user));
    }

    public LoginStep login(String email, String password) {
        User user = users.findByEmail(AuthValidator.normalizeEmail(email == null ? "" : email)).orElse(null);
        if (user == null) {
            encoder.matches(password == null ? "" : password, dummyHash);
            throw invalid();
        }
        assertNotLocked(user);
        if (password == null || !encoder.matches(password, user.getPasswordHash())) {
            registerFailure(user);
            throw invalid();
        }
        if (!user.isTotpEnabled()) {
            // Password is right but the authenticator app was never set up: let them finish setup now.
            return new LoginStep("SETUP_REQUIRED", null, setupInfo(user, "SETUP_REQUIRED"));
        }
        return new LoginStep("MFA_REQUIRED", jwt.issue(user.getId().toString(), "mfa", STEP_TTL_SEC), null);
    }

    public Session loginVerify(String mfaToken, String code) {
        User user = userFromToken(mfaToken, "mfa");
        assertNotLocked(user);
        acceptCode(user, code);
        return session(users.save(user));
    }

    // ---------------------------------------------------------------------------------------------

    private void acceptCode(User user, String code) {
        long step = TotpService.verify(user.getTotpSecret(), code, System.currentTimeMillis(), user.getLastTotpStep());
        if (step < 0) {
            registerFailure(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "That code is wrong or was already used.");
        }
        user.setLastTotpStep(step);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
    }

    private void registerFailure(User user) {
        int n = user.getFailedAttempts() + 1;
        if (n >= MAX_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCK_TIME));
            n = 0;
        }
        user.setFailedAttempts(n);
        users.save(user);
    }

    private void assertNotLocked(User user) {
        Instant until = user.getLockedUntil();
        if (until != null && until.isAfter(Instant.now())) {
            long mins = Math.max(1, Duration.between(Instant.now(), until).toMinutes() + 1);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. This account is locked for about " + mins + " more minute(s).");
        }
    }

    private User userFromToken(String token, String scope) {
        JwtService.Claims c = jwt.verify(token);
        if (c == null || !scope.equals(c.scope())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This step has expired. Please start again.");
        }
        return users.findById(UUID.fromString(c.subject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown account."));
    }

    private SetupInfo setupInfo(User user, String status) {
        return new SetupInfo(status, user.getId().toString(),
                jwt.issue(user.getId().toString(), "setup", 10 * 60),
                user.getTotpSecret(), TotpService.otpauthUri(ISSUER, user.getEmail(), user.getTotpSecret()));
    }

    private Session session(User user) {
        String token = jwt.issue(user.getId().toString(), "access", ACCESS_TTL_SEC);
        return new Session(token, user.getId().toString(), user.getName(),
                System.currentTimeMillis() + ACCESS_TTL_SEC * 1000);
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }
}
