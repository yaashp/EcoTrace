package com.ecotrace.service;

import com.ecotrace.domain.AppUser;
import com.ecotrace.domain.Partner;
import com.ecotrace.domain.PartnerType;
import com.ecotrace.domain.Role;
import com.ecotrace.dto.Dtos.CreateUserRequest;
import com.ecotrace.dto.Dtos.PageView;
import com.ecotrace.dto.Dtos.UserView;
import com.ecotrace.repo.PartnerRepository;
import com.ecotrace.repo.SessionRepository;
import com.ecotrace.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Administrator-only user management. */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final UserRepository users;
    private final PartnerRepository partners;
    private final SessionRepository sessions;
    private final AuthService auth;

    public UserAdminService(UserRepository users, PartnerRepository partners, SessionRepository sessions, AuthService auth) {
        this.users = users;
        this.partners = partners;
        this.sessions = sessions;
        this.auth = auth;
    }

    @Transactional(readOnly = true)
    public PageView<UserView> list(String role, String q, int page, int size) {
        List<Role> roles = role == null || role.isBlank() ? Arrays.asList(Role.values()) : List.of(parseRole(role));
        var pageable = Paging.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        return Paging.view(users.search(roles, Paging.likePattern(q), pageable), UserAdminService::toView);
    }

    @Transactional
    public UserView create(CreateUserRequest req) {
        Role role = parseRole(req.role());
        String email = AuthService.normalizeEmail(req.email());
        Map<String, String> errors = new java.util.LinkedHashMap<>();
        if (users.existsByEmail(email)) {
            errors.put("email", "An account with this email already exists");
        }
        String passwordError = PasswordRules.check(req.password());
        if (passwordError != null) {
            errors.put("password", passwordError);
        }
        Partner partner = null;
        if (role == Role.RECYCLER) {
            partner = req.partnerId() == null ? null : partners.findById(req.partnerId()).orElse(null);
            if (partner == null || partner.getType() != PartnerType.RECYCLER) {
                errors.put("partnerId", "Choose the recycling partner this account works for");
            }
        } else if (req.partnerId() != null) {
            errors.put("partnerId", "Only recycler accounts are linked to a partner");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", errors);
        }
        AppUser user = new AppUser(req.name().trim(), email, blankToNull(req.phone()), auth.hashPassword(req.password()), role);
        user.setPartner(partner);
        AppUser saved = users.save(user);
        log.info("User id {} created with role {}", saved.getId(), role);
        return toView(saved);
    }

    @Transactional
    public UserView setActive(AppUser actor, Long id, boolean active) {
        if (actor.getId().equals(id) && !active) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot deactivate your own account.");
        }
        AppUser user = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
        user.setActive(active);
        users.save(user);
        if (!active) {
            sessions.deleteForUser(id);
        }
        log.info("User id {} set active={}", id, active);
        return toView(user);
    }

    public static UserView toView(AppUser u) {
        Partner p = u.getPartner();
        return new UserView(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole().name(), u.getRole().label(),
                p == null ? null : p.getId(), p == null ? null : p.getName(), u.isActive(), u.isDemo(), u.getCreatedAt());
    }

    private static Role parseRole(String raw) {
        try {
            return Role.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", Map.of("role", "Unknown role"));
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
