package com.ecotrace.service;

import com.ecotrace.domain.Catalog;
import com.ecotrace.domain.Partner;
import com.ecotrace.domain.PartnerType;
import com.ecotrace.domain.Verification;
import com.ecotrace.dto.Dtos.PartnerRequest;
import com.ecotrace.dto.Dtos.PartnerView;
import com.ecotrace.repo.PartnerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PartnerService {

    private static final Set<String> SERVICES = Set.of("DOORSTEP", "DROP_OFF");

    private final PartnerRepository partners;

    public PartnerService(PartnerRepository partners) {
        this.partners = partners;
    }

    /** Public directory: active entries only, filtered in memory (the directory is small). */
    @Transactional(readOnly = true)
    public List<PartnerView> search(String q, String category, String type, String verification) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        return partners.findByActiveTrueOrderByNameAsc().stream()
                .filter(p -> needle.isEmpty() || (p.getName() + " " + p.getLocality() + " " + p.getCity() + " "
                        + nullToEmpty(p.getServiceArea())).toLowerCase().contains(needle))
                .filter(p -> isBlank(category) || split(p.getAcceptedCategories()).contains(category))
                .filter(p -> isBlank(type) || p.getType().name().equalsIgnoreCase(type))
                .filter(p -> isBlank(verification) || p.getVerification().name().equalsIgnoreCase(verification))
                .map(PartnerService::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerView> adminList() {
        return partners.findAllByOrderByNameAsc().stream().map(PartnerService::toView).toList();
    }

    @Transactional
    public PartnerView create(PartnerRequest req) {
        Partner p = new Partner(req.name().trim(), parseType(req.type()));
        apply(p, req, Verification.PENDING);
        return toView(partners.save(p));
    }

    @Transactional
    public PartnerView update(Long id, PartnerRequest req) {
        Partner p = partners.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Partner not found."));
        p.setName(req.name().trim());
        p.setType(parseType(req.type()));
        apply(p, req, p.getVerification());
        return toView(partners.save(p));
    }

    @Transactional
    public PartnerView setActive(Long id, boolean active) {
        Partner p = partners.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Partner not found."));
        p.setActive(active);
        return toView(partners.save(p));
    }

    private void apply(Partner p, PartnerRequest req, Verification defaultVerification) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (String c : req.acceptedCategories()) {
            if (!Catalog.CATEGORIES.contains(c)) {
                errors.put("acceptedCategories", "Unknown category: " + c);
            }
        }
        List<String> services = req.services() == null ? List.of() : req.services();
        for (String s : services) {
            if (!SERVICES.contains(s)) {
                errors.put("services", "Unknown service: " + s);
            }
        }
        Verification verification = defaultVerification;
        if (!isBlank(req.verification())) {
            try {
                verification = Verification.valueOf(req.verification().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.put("verification", "Unknown verification status");
            }
        }
        if (verification == Verification.VERIFIED && isBlank(req.verificationNote())) {
            errors.put("verificationNote", "Record how this partner was verified before marking it verified");
        }
        if (req.lat() != null && (req.lat() < -90 || req.lat() > 90)) {
            errors.put("lat", "Latitude must be between -90 and 90");
        }
        if (req.lng() != null && (req.lng() < -180 || req.lng() > 180)) {
            errors.put("lng", "Longitude must be between -180 and 180");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", errors);
        }
        p.setAddress(req.address().trim());
        p.setCity(req.city().trim());
        p.setLocality(req.locality().trim());
        p.setServiceArea(blankToNull(req.serviceArea()));
        p.setAcceptedCategories(String.join(",", req.acceptedCategories()));
        p.setContactEmail(blankToNull(req.contactEmail()));
        p.setContactPhone(blankToNull(req.contactPhone()));
        p.setServices(String.join(",", services));
        p.setVerification(verification);
        p.setVerificationNote(blankToNull(req.verificationNote()));
        p.setLat(req.lat());
        p.setLng(req.lng());
        if (req.active() != null) {
            p.setActive(req.active());
        }
    }

    private static PartnerType parseType(String raw) {
        try {
            return PartnerType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields.", Map.of("type", "Unknown partner type"));
        }
    }

    public static PartnerView toView(Partner p) {
        return new PartnerView(p.getId(), p.getName(), p.getType().name(), p.getType().label(), p.getAddress(),
                p.getCity(), p.getLocality(), p.getServiceArea(), split(p.getAcceptedCategories()),
                p.getContactEmail(), p.getContactPhone(), split(p.getServices()), p.getVerification().name(),
                p.getVerification().label(), p.getVerificationNote(), p.getLat(), p.getLng(), p.isActive(), p.isDemo());
    }

    private static List<String> split(String csv) {
        return csv == null || csv.isBlank() ? List.of() : Arrays.stream(csv.split(",")).map(String::trim).toList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
