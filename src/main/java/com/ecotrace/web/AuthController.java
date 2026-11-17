package com.ecotrace.web;

import com.ecotrace.domain.AppUser;
import com.ecotrace.domain.Catalog;
import com.ecotrace.domain.Outcome;
import com.ecotrace.domain.PickupStatus;
import com.ecotrace.dto.Dtos.ConfigView;
import com.ecotrace.dto.Dtos.LoginRequest;
import com.ecotrace.dto.Dtos.MetaView;
import com.ecotrace.dto.Dtos.RegisterRequest;
import com.ecotrace.dto.Dtos.UserView;
import com.ecotrace.service.AuthService;
import com.ecotrace.service.SeedService;
import com.ecotrace.service.UserAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;
    private final SeedService seed;

    public AuthController(AuthService auth, SeedService seed) {
        this.auth = auth;
        this.seed = seed;
    }

    @GetMapping("/config")
    public ConfigView config() {
        return seed.config();
    }

    @GetMapping("/meta")
    public MetaView meta() {
        Map<String, String> outcomes = new LinkedHashMap<>();
        Arrays.stream(Outcome.values()).forEach(o -> outcomes.put(o.name(), o.label()));
        Map<String, String> statuses = new LinkedHashMap<>();
        Arrays.stream(PickupStatus.values()).forEach(s -> statuses.put(s.name(), s.label()));
        return new MetaView(Catalog.CATEGORIES, Catalog.CONDITIONS, Catalog.SLOTS, outcomes, statuses);
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        AppUser user = auth.register(request);
        auth.writeCookie(response, auth.openSession(user));
        return UserAdminService.toView(user);
    }

    @PostMapping("/auth/login")
    public UserView login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AppUser user = auth.authenticate(request.email(), request.password());
        auth.writeCookie(response, auth.openSession(user));
        return UserAdminService.toView(user);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(request);
        auth.clearCookie(response);
    }

    /** Returns the signed-in user, or an empty body when nobody is signed in. */
    @GetMapping("/auth/me")
    public UserView me(HttpServletRequest request) {
        AppUser user = auth.currentUser(request);
        return user == null ? null : UserAdminService.toView(user);
    }
}
