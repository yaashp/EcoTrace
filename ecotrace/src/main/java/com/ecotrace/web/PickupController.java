package com.ecotrace.web;

import com.ecotrace.domain.AppUser;
import com.ecotrace.domain.Role;
import com.ecotrace.dto.Dtos.BookingRequest;
import com.ecotrace.dto.Dtos.PageView;
import com.ecotrace.dto.Dtos.PickupDetail;
import com.ecotrace.dto.Dtos.PickupSummary;
import com.ecotrace.dto.Dtos.TransitionRequest;
import com.ecotrace.service.AuthService;
import com.ecotrace.service.PickupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Booking and the signed-in views of a pickup. Visibility and permissions are enforced in PickupService. */
@RestController
@RequestMapping("/api/pickups")
public class PickupController {

    private final AuthService auth;
    private final PickupService pickups;

    public PickupController(AuthService auth, PickupService pickups) {
        this.auth = auth;
        this.pickups = pickups;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PickupDetail book(HttpServletRequest http, @Valid @RequestBody BookingRequest request) {
        AppUser customer = auth.require(http, Role.CUSTOMER);
        return pickups.book(customer, request);
    }

    @GetMapping
    public PageView<PickupSummary> list(
            HttpServletRequest http,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        AppUser actor = auth.requireAny(http);
        return pickups.list(actor, status, q, page, size);
    }

    @GetMapping("/{reference}")
    public PickupDetail detail(HttpServletRequest http, @PathVariable String reference) {
        AppUser actor = auth.requireAny(http);
        return pickups.detail(actor, reference);
    }

    @PostMapping("/{reference}/transition")
    public PickupDetail transition(HttpServletRequest http, @PathVariable String reference,
                                   @Valid @RequestBody TransitionRequest request) {
        AppUser actor = auth.requireAny(http);
        return pickups.transition(actor, reference, request);
    }
}
