package com.ecotrace.web;

import com.ecotrace.domain.AppUser;
import com.ecotrace.domain.Role;
import com.ecotrace.dto.Dtos.ActiveRequest;
import com.ecotrace.dto.Dtos.AuditView;
import com.ecotrace.dto.Dtos.CreateUserRequest;
import com.ecotrace.dto.Dtos.HandoverListItem;
import com.ecotrace.dto.Dtos.Metrics;
import com.ecotrace.dto.Dtos.PageView;
import com.ecotrace.dto.Dtos.PartnerRequest;
import com.ecotrace.dto.Dtos.PartnerView;
import com.ecotrace.dto.Dtos.UserView;
import com.ecotrace.service.AuditService;
import com.ecotrace.service.AuthService;
import com.ecotrace.service.HandoverService;
import com.ecotrace.service.MetricsService;
import com.ecotrace.service.PartnerService;
import com.ecotrace.service.SeedService;
import com.ecotrace.service.UserAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Administrator-only endpoints. Every method re-checks the role on the server. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuthService auth;
    private final MetricsService metrics;
    private final UserAdminService userAdmin;
    private final PartnerService partners;
    private final AuditService audits;
    private final SeedService seed;
    private final HandoverService handovers;

    public AdminController(AuthService auth, MetricsService metrics, UserAdminService userAdmin,
                           PartnerService partners, AuditService audits, SeedService seed,
                           HandoverService handovers) {
        this.auth = auth;
        this.metrics = metrics;
        this.userAdmin = userAdmin;
        this.partners = partners;
        this.audits = audits;
        this.seed = seed;
        this.handovers = handovers;
    }

    @GetMapping("/metrics")
    public Metrics metrics(HttpServletRequest http,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(defaultValue = "true") boolean includeDemo) {
        auth.require(http, Role.ADMIN);
        return metrics.compute(from, to, includeDemo);
    }

    @GetMapping("/users")
    public PageView<UserView> users(HttpServletRequest http,
                                    @RequestParam(required = false) String role,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "10") int size) {
        auth.require(http, Role.ADMIN);
        return userAdmin.list(role, q, page, size);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(HttpServletRequest http, @Valid @RequestBody CreateUserRequest request) {
        auth.require(http, Role.ADMIN);
        return userAdmin.create(request);
    }

    @PostMapping("/users/{id}/active")
    public UserView setUserActive(HttpServletRequest http, @PathVariable Long id, @RequestBody ActiveRequest request) {
        AppUser admin = auth.require(http, Role.ADMIN);
        return userAdmin.setActive(admin, id, request.active());
    }

    @GetMapping("/partners")
    public List<PartnerView> partners(HttpServletRequest http) {
        auth.require(http, Role.ADMIN);
        return partners.adminList();
    }

    @PostMapping("/partners")
    @ResponseStatus(HttpStatus.CREATED)
    public PartnerView createPartner(HttpServletRequest http, @Valid @RequestBody PartnerRequest request) {
        auth.require(http, Role.ADMIN);
        return partners.create(request);
    }

    @PutMapping("/partners/{id}")
    public PartnerView updatePartner(HttpServletRequest http, @PathVariable Long id, @Valid @RequestBody PartnerRequest request) {
        auth.require(http, Role.ADMIN);
        return partners.update(id, request);
    }

    @PostMapping("/partners/{id}/active")
    public PartnerView setPartnerActive(HttpServletRequest http, @PathVariable Long id, @RequestBody ActiveRequest request) {
        auth.require(http, Role.ADMIN);
        return partners.setActive(id, request.active());
    }

    @GetMapping("/audit")
    public PageView<AuditView> audit(HttpServletRequest http,
                                     @RequestParam(defaultValue = "false") boolean failedOnly,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "15") int size) {
        auth.require(http, Role.ADMIN);
        return audits.list(failedOnly, page, size);
    }

    /** Verified handovers, newest first. Each record exists only after a collector entered the customer's code. */
    @GetMapping("/handovers")
    public PageView<HandoverListItem> handovers(HttpServletRequest http,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "15") int size) {
        auth.require(http, Role.ADMIN);
        return handovers.list(page, size);
    }

    @PostMapping("/demo/reset")
    public Map<String, String> resetDemo(HttpServletRequest http) {
        auth.require(http, Role.ADMIN);
        seed.resetDemo();
        return Map.of("message", "Demonstration data was reset. Real accounts and bookings were not changed.");
    }
}
