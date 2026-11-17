package com.ecotrace.web;

import com.ecotrace.domain.AppUser;
import com.ecotrace.domain.Role;
import com.ecotrace.dto.Dtos.HandoverCodeIssued;
import com.ecotrace.dto.Dtos.HandoverView;
import com.ecotrace.dto.Dtos.VerifyHandoverRequest;
import com.ecotrace.service.AuthService;
import com.ecotrace.service.HandoverService;
import com.ecotrace.service.HandoverService.PhotoFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Verified handover endpoints for one pickup. Every method checks the role here and the ownership or assignment
 * in HandoverService. Responses that carry a code or a photo are marked no-store.
 */
@RestController
@RequestMapping("/api/pickups/{reference}/handover")
public class HandoverController {

    private final AuthService auth;
    private final HandoverService handovers;

    public HandoverController(AuthService auth, HandoverService handovers) {
        this.auth = auth;
        this.handovers = handovers;
    }

    /** Customer: create a new one-time code. The code is returned only in this response. */
    @PostMapping("/code")
    public ResponseEntity<HandoverCodeIssued> issueCode(HttpServletRequest http, @PathVariable String reference) {
        AppUser customer = auth.require(http, Role.CUSTOMER);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(handovers.issueCode(customer, reference));
    }

    /** Assigned collector: check the code the customer shared. */
    @PostMapping("/verify")
    public HandoverView verify(HttpServletRequest http, @PathVariable String reference,
                               @Valid @RequestBody VerifyHandoverRequest request) {
        AppUser collector = auth.require(http, Role.COLLECTOR);
        return handovers.verify(collector, reference, request.code());
    }

    /** Assigned collector: add or replace the required geotagged photo, before the collection is recorded. */
    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HandoverView uploadPhoto(HttpServletRequest http, @PathVariable String reference,
                                    @RequestParam(value = "photo", required = false) MultipartFile photo) {
        AppUser collector = auth.require(http, Role.COLLECTOR);
        return handovers.attachPhoto(collector, reference, photo);
    }

    /** Customer, assigned collector or administrator: the handover photo, if one exists. */
    @GetMapping("/photo")
    public ResponseEntity<byte[]> photo(HttpServletRequest http, @PathVariable String reference) {
        AppUser actor = auth.requireAny(http);
        PhotoFile file = handovers.photo(actor, reference);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(file.data());
    }
}
