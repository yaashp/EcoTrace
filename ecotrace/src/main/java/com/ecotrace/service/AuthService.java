package com.ecotrace.service;

import com.ecotrace.domain.AppUser;
import com.ecotrace.domain.Role;
import com.ecotrace.domain.UserSession;
import com.ecotrace.dto.Dtos.PasswordChangeRequest;
import com.ecotrace.dto.Dtos.ProfileRequest;
import com.ecotrace.dto.Dtos.RegisterRequest;
import com.ecotrace.repo.SessionRepository;
import com.ecotrace.repo.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registration, login, sessions and role checks.
 * Sessions are random tokens in an HttpOnly cookie; only a SHA-256 hash of each token is stored.
 * Passwords are hashed with BCrypt. This is a small custom layer; for a production system, replace it
 * with Spring Security and an identity provider (see README).
 */
@Service
public class AuthService {

    public static final String COOKIE_NAME = "ECOTRACE_SESSION";
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_LOGINS = 5;
    private static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);

    private record Attempts(int count, Instant first) {
    }

    private final UserRepository users;
    private final SessionRepository sessions;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Attempts> failedLogins = new ConcurrentHashMap<>();
    private final String dummyHash;

    @Value("${ecotrace.session-hours:8}")
    private long sessionHours;

    @Value("${ecotrace.cookie-secure:false}")
    private boolean cookieSecure;

    public AuthService(UserRepository users, SessionRepository sessions) {
        this.users = users;
        this.sessions = sessions;
        this.dummyHash = encoder.encode("not-a-real-password-0");
    }

    // ---------- registration and login ----------

    @Transactional
    public AppUser register(RegisterRequest req) {
        String email = normalizeEmail(req.email());
        String passwordError = PasswordRules.check(req.password());
        if (passwordError != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", Map.of("password", passwordError));
        }
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Please correct the highlighted fields.",
                    Map.of("email", "An account with this email already exists"));
        }
        AppUser user = new AppUser(req.name().trim(), email, blankToNull(req.phone()), encoder.encode(req.password()), Role.CUSTOMER);
        return users.save(user);
    }

    @Transactional
    public AppUser authenticate(String rawEmail, String password) {
        String email = normalizeEmail(rawEmail);
        Attempts attempts = failedLogins.get(email);
        if (attempts != null && attempts.count() >= MAX_FAILED_LOGINS
                && attempts.first().plus(LOCKOUT_WINDOW).isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts. Try again in a few minutes.");
        }
        AppUser user = users.findByEmail(email).orElse(null);
        // Always run one BCrypt comparison so timing does not reveal whether the email exists.
        boolean ok = encoder.matches(password, user != null ? user.getPasswordHash() : dummyHash) && user != null;
        if (!ok || !user.isActive()) {
            recordFailure(email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        failedLogins.remove(email);
        return user;
    }

    /** Creates a session and returns the raw token, which is only ever sent to the browser in the cookie. */
    @Transactional
    public String openSession(AppUser user) {
        sessions.deleteExpired(Instant.now());
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.save(new UserSession(hash(token), user, Instant.now().plus(Duration.ofHours(sessionHours))));
        return token;
    }

    public void writeCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/")
                .maxAge(Duration.ofHours(sessionHours)).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/").maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Transactional
    public void logout(HttpServletRequest request) {
        String token = tokenOf(request);
        if (token != null) {
            sessions.deleteByHash(hash(token));
        }
    }

    // ---------- current user ----------

    /** Returns the signed-in user, or null when there is no valid session. */
    @Transactional(readOnly = true)
    public AppUser currentUser(HttpServletRequest request) {
        String token = tokenOf(request);
        if (token == null) {
            return null;
        }
        UserSession session = sessions.findByTokenHash(hash(token)).orElse(null);
        if (session == null || session.getExpiresAt().isBefore(Instant.now()) || !session.getUser().isActive()) {
            return null;
        }
        return session.getUser();
    }

    /** 401 when nobody is signed in, 403 when the role is not allowed. */
    public AppUser require(HttpServletRequest request, Role... allowed) {
        AppUser user = currentUser(request);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
        }
        for (Role role : allowed) {
            if (user.getRole() == role) {
                return user;
            }
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Your account is not allowed to do this.");
    }

    public AppUser requireAny(HttpServletRequest request) {
        return require(request, Role.values());
    }

    // ---------- account ----------

    @Transactional
    public AppUser updateProfile(AppUser actor, ProfileRequest req) {
        AppUser user = users.findById(actor.getId()).orElseThrow();
        user.setName(req.name().trim());
        user.setPhone(blankToNull(req.phone()));
        return users.save(user);
    }

    @Transactional
    public void changePassword(AppUser actor, PasswordChangeRequest req) {
        AppUser user = users.findById(actor.getId()).orElseThrow();
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.",
                    Map.of("currentPassword", "Current password is incorrect"));
        }
        String error = PasswordRules.check(req.newPassword());
        if (error != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", Map.of("newPassword", error));
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        users.save(user);
        log.info("Password changed for user id {}", user.getId());
    }

    public String hashPassword(String raw) {
        return encoder.encode(raw);
    }

    // ---------- helpers ----------

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void recordFailure(String email) {
        Instant now = Instant.now();
        failedLogins.merge(email, new Attempts(1, now), (old, ignored) ->
                old.first().plus(LOCKOUT_WINDOW).isBefore(now) ? new Attempts(1, now) : new Attempts(old.count() + 1, old.first()));
        log.warn("Failed login attempt");
    }

    private static String tokenOf(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
