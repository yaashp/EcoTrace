package com.ecotrace.service;

import com.ecotrace.domain.AppUser;
import com.ecotrace.domain.AuditLog;
import com.ecotrace.domain.Handover;
import com.ecotrace.domain.HandoverCode;
import com.ecotrace.domain.HandoverPhoto;
import com.ecotrace.domain.HandoverRules;
import com.ecotrace.domain.Pickup;
import com.ecotrace.domain.PickupStatus;
import com.ecotrace.domain.Role;
import com.ecotrace.dto.Dtos.HandoverCodeIssued;
import com.ecotrace.dto.Dtos.HandoverCodeView;
import com.ecotrace.dto.Dtos.HandoverListItem;
import com.ecotrace.dto.Dtos.HandoverView;
import com.ecotrace.dto.Dtos.PageView;
import com.ecotrace.repo.AuditLogRepository;
import com.ecotrace.repo.HandoverCodeRepository;
import com.ecotrace.repo.HandoverPhotoRepository;
import com.ecotrace.repo.HandoverRepository;
import com.ecotrace.repo.PickupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Verified physical handover. The customer issues a one-time code, the collector enters it, and only then can
 * the pickup be recorded as collected. Codes are stored as BCrypt hashes, expire, allow a limited number of wrong
 * guesses, and cannot be reused. Failed attempts and successful verifications are written to the audit log
 * without the code itself.
 */
@Service
public class HandoverService {

    /** Result of a photo read: the content type from the file's signature, and the bytes. */
    public record PhotoFile(String contentType, byte[] data) {
    }

    private static final Logger log = LoggerFactory.getLogger(HandoverService.class);
    private static final String NOT_FOUND = "Pickup not found.";
    private static final String LIMIT_REACHED = "Too many incorrect attempts. Ask the customer to generate a new code.";

    private final PickupRepository pickups;
    private final HandoverRepository handovers;
    private final HandoverCodeRepository codes;
    private final HandoverPhotoRepository photos;
    private final AuditLogRepository audits;
    private final NotificationService notifications;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    @Value("${ecotrace.handover-code-minutes:30}")
    private long codeMinutes;

    public HandoverService(PickupRepository pickups, HandoverRepository handovers, HandoverCodeRepository codes,
                           HandoverPhotoRepository photos, AuditLogRepository audits, NotificationService notifications) {
        this.pickups = pickups;
        this.handovers = handovers;
        this.codes = codes;
        this.photos = photos;
        this.audits = audits;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------ what a viewer may do next

    @Transactional(readOnly = true)
    public boolean hasPhoto(Pickup pickup) {
        return handovers.findByPickupId(pickup.getId()).map(h -> photos.existsByHandoverId(h.getId())).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isVerified(Pickup pickup) {
        return handovers.existsByPickupId(pickup.getId());
    }

    @Transactional(readOnly = true)
    public Optional<Handover> findFor(Pickup pickup) {
        return handovers.findByPickupId(pickup.getId());
    }

    public boolean canIssueCode(Pickup p, AppUser viewer) {
        return viewer.getRole() == Role.CUSTOMER && p.getCustomer().getId().equals(viewer.getId())
                && p.getStatus() == PickupStatus.CONFIRMED && p.getCollector() != null && !isVerified(p);
    }

    public boolean canVerify(Pickup p, AppUser viewer) {
        return isAssignedCollector(viewer, p) && p.getStatus() == PickupStatus.CONFIRMED && !isVerified(p);
    }

    public boolean canAttachPhoto(Pickup p, AppUser viewer) {
        if (!isAssignedCollector(viewer, p)) {
            return false;
        }
        return findFor(p).map(h -> h.getCollectedAt() == null).orElse(false);
    }

    /** Verified details visible to this viewer. Recycling partners never see handover records. */
    @Transactional(readOnly = true)
    public HandoverView viewFor(Pickup pickup, AppUser viewer) {
        if (viewer.getRole() == Role.RECYCLER) {
            return null;
        }
        return findFor(pickup).map(h -> toView(h, viewer)).orElse(null);
    }

    /** Status of the current code, for the customer only. Never includes the code. */
    @Transactional(readOnly = true)
    public HandoverCodeView codeStatusFor(Pickup pickup, AppUser viewer) {
        if (viewer.getRole() != Role.CUSTOMER || !pickup.getCustomer().getId().equals(viewer.getId())) {
            return null;
        }
        Instant now = Instant.now();
        return codes.findOpen(pickup.getId()).stream()
                .filter(c -> c.isUsable(now))
                .findFirst()
                .map(c -> new HandoverCodeView(true, c.getExpiresAt(), c.attemptsLeft()))
                .orElse(null);
    }

    // ------------------------------------------------------------------ customer: issue a code

    /**
     * Creates a new code and returns it once. Any earlier open code for the pickup is revoked, so only the newest
     * code works.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public HandoverCodeIssued issueCode(AppUser customer, String reference) {
        Pickup pickup = load(reference);
        if (!pickup.getCustomer().getId().equals(customer.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        if (pickup.getStatus() != PickupStatus.CONFIRMED || pickup.getCollector() == null) {
            throw refuse(customer, pickup, HttpStatus.CONFLICT, "Collection code refused: pickup is not confirmed with a collector.",
                    "A collection code can be generated once a collector is assigned to a confirmed pickup.", Map.of());
        }
        if (isVerified(pickup)) {
            throw refuse(customer, pickup, HttpStatus.CONFLICT, "Collection code refused: handover already verified.",
                    "This pickup already has a verified handover.", Map.of());
        }

        Instant now = Instant.now();
        for (HandoverCode previous : codes.findOpenForUpdate(pickup.getId())) {
            previous.revoke(now);
        }
        String code = HandoverRules.newCode(random);
        Instant expiresAt = now.plus(Duration.ofMinutes(codeMinutes));
        codes.save(new HandoverCode(pickup, customer, encoder.encode(code), expiresAt, now));

        audit(customer, pickup, true, "Collection code generated");
        notifications.notify(pickup.getCollector(), "HANDOVER_CODE_ISSUED", "Collection code generated",
                "The customer generated a collection code for pickup " + pickup.getReference()
                        + ". Ask for the code when you arrive.", pickup.getReference());
        log.info("Collection code issued for pickup {}", pickup.getReference());
        return new HandoverCodeIssued(code, expiresAt, HandoverRules.MAX_ATTEMPTS);
    }

    // ------------------------------------------------------------------ collector: verify a code

    /**
     * Checks the code the customer gave the assigned collector. Order of checks: assignment, format, pickup state,
     * existing handover, an open code, expiry, attempt limit, then the code itself. A wrong guess is counted and
     * audited; the transaction still commits so the count is kept.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public HandoverView verify(AppUser collector, String reference, String rawCode) {
        Pickup pickup = load(reference);
        if (!isAssignedCollector(collector, pickup)) {
            throw refuse(collector, pickup, HttpStatus.NOT_FOUND, "Blocked: pickup is not assigned to this collector.",
                    NOT_FOUND, Map.of());
        }
        String code = rawCode == null ? "" : rawCode.trim();
        if (!HandoverRules.isWellFormedCode(code)) {
            throw refuse(collector, pickup, HttpStatus.BAD_REQUEST, "Handover code rejected: not a 6-digit code.",
                    "Enter the 6-digit code.", Map.of("code", "Enter the 6-digit code"));
        }
        if (pickup.getStatus() != PickupStatus.CONFIRMED) {
            throw refuse(collector, pickup, HttpStatus.CONFLICT, "Handover refused: pickup is not confirmed.",
                    "Handover codes can only be checked for a confirmed pickup.", Map.of());
        }
        if (isVerified(pickup)) {
            throw refuse(collector, pickup, HttpStatus.CONFLICT, "Handover refused: already verified.",
                    "This pickup already has a verified handover.", Map.of());
        }

        Instant now = Instant.now();
        HandoverCode active = codes.findOpenForUpdate(pickup.getId()).stream().findFirst().orElse(null);
        if (active == null) {
            String msg = "There is no active collection code. Ask the customer to generate one.";
            throw refuse(collector, pickup, HttpStatus.CONFLICT, "Handover refused: no active code.", msg, Map.of("code", msg));
        }
        if (!active.getExpiresAt().isAfter(now)) {
            String msg = "This code has expired. Ask the customer to generate a new one.";
            throw refuse(collector, pickup, HttpStatus.CONFLICT, "Handover refused: code expired.", msg, Map.of("code", msg));
        }
        if (active.attemptsLeft() == 0) {
            throw refuse(collector, pickup, HttpStatus.TOO_MANY_REQUESTS, "Handover refused: attempt limit reached.",
                    LIMIT_REACHED, Map.of("code", LIMIT_REACHED));
        }
        if (!encoder.matches(code, active.getCodeHash())) {
            active.recordFailure();
            codes.save(active);
            int left = active.attemptsLeft();
            String msg = left == 0 ? LIMIT_REACHED
                    : "The code is incorrect. " + left + (left == 1 ? " attempt" : " attempts") + " left.";
            audit(collector, pickup, false, "Handover code rejected (attempt " + active.getFailedAttempts()
                    + " of " + HandoverRules.MAX_ATTEMPTS + ")");
            throw new ApiException(left == 0 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST, msg, Map.of("code", msg));
        }

        active.markUsed(now);
        codes.save(active);
        Handover handover = handovers.save(new Handover(newUniqueReference(), pickup, pickup.getCustomer(), collector, now));
        audit(collector, pickup, true, "Handover verified: " + handover.getHandoverReference());
        notifications.notify(pickup.getCustomer(), "HANDOVER_VERIFIED", "Handover verified",
                "Your collector verified the collection code for pickup " + pickup.getReference()
                        + ". Handover reference " + handover.getHandoverReference() + ".", pickup.getReference());
        log.info("Handover {} verified for pickup {}", handover.getHandoverReference(), pickup.getReference());
        return toView(handover, collector);
    }

    /**
     * Called by PickupService when the collector records the pickup as collected. The transition is refused
     * unless a verified handover exists, so this only completes a handover that has already been verified.
     */
    @Transactional
    public String recordCollection(Pickup pickup, Instant at) {
        Handover handover = handovers.findByPickupId(pickup.getId()).orElseThrow(() ->
                new ApiException(HttpStatus.CONFLICT, "Verify the customer's collection code before marking this pickup as collected."));
        handover.markCollected(at);
        handovers.save(handover);
        return handover.getHandoverReference();
    }

    // ------------------------------------------------------------------ optional photo evidence

    /** Adds or replaces the photo of a handover. Allowed only for the assigned collector, before collection is recorded. */
    @Transactional(noRollbackFor = ApiException.class)
    public HandoverView attachPhoto(AppUser collector, String reference, MultipartFile upload) {
        Pickup pickup = load(reference);
        if (!isAssignedCollector(collector, pickup)) {
            throw refuse(collector, pickup, HttpStatus.NOT_FOUND, "Blocked: pickup is not assigned to this collector.",
                    NOT_FOUND, Map.of());
        }
        Handover handover = handovers.findByPickupId(pickup.getId()).orElseThrow(() ->
                refuse(collector, pickup, HttpStatus.CONFLICT, "Photo refused: no verified handover.",
                        "Verify the collection code before adding a photo.", Map.of()));
        if (handover.getCollectedAt() != null) {
            throw refuse(collector, pickup, HttpStatus.CONFLICT, "Photo refused: handover already recorded as collected.",
                    "The handover is already recorded as collected, so the photo can no longer be changed.", Map.of());
        }
        if (upload == null || upload.isEmpty()) {
            throw refuse(collector, pickup, HttpStatus.BAD_REQUEST, "Photo refused: empty upload.",
                    "Choose a photo to upload.", Map.of("photo", "Choose a photo to upload"));
        }
        if (upload.getSize() > HandoverRules.MAX_PHOTO_BYTES) {
            throw refuse(collector, pickup, HttpStatus.BAD_REQUEST, "Photo refused: too large.",
                    "The photo is larger than 3 MB. Choose a smaller image.", Map.of("photo", "Use an image under 3 MB"));
        }
        byte[] bytes;
        try {
            bytes = upload.getBytes();
        } catch (IOException e) {
            throw refuse(collector, pickup, HttpStatus.BAD_REQUEST, "Photo refused: could not be read.",
                    "The photo could not be read. Try another image.", Map.of("photo", "The photo could not be read"));
        }
        String type = HandoverRules.detectImageType(bytes);
        if (type == null) {
            throw refuse(collector, pickup, HttpStatus.BAD_REQUEST, "Photo refused: not a JPEG, PNG or WebP image.",
                    "Upload a JPEG, PNG or WebP image.", Map.of("photo", "Upload a JPEG, PNG or WebP image"));
        }

        Instant now = Instant.now();
        HandoverPhoto photo = photos.findByHandoverId(handover.getId()).orElse(null);
        if (photo == null) {
            photos.save(new HandoverPhoto(handover, type, bytes, now));
        } else {
            photo.replace(type, bytes, now);
            photos.save(photo);
        }
        audit(collector, pickup, true, "Handover photo saved for " + handover.getHandoverReference());
        return toView(handover, collector);
    }

    /** Photo bytes for the customer, the assigned collector, or an administrator. Everyone else gets 404. */
    @Transactional(readOnly = true)
    public PhotoFile photo(AppUser actor, String reference) {
        Pickup pickup = load(reference);
        boolean customerOwns = actor.getRole() == Role.CUSTOMER && pickup.getCustomer().getId().equals(actor.getId());
        boolean allowed = actor.getRole() == Role.ADMIN || customerOwns || isAssignedCollector(actor, pickup);
        if (!allowed) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        String missing = "No photo has been added to this handover.";
        Handover handover = handovers.findByPickupId(pickup.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, missing));
        HandoverPhoto photo = photos.findByHandoverId(handover.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, missing));
        return new PhotoFile(photo.getContentType(), photo.getData());
    }

    // ------------------------------------------------------------------ administrator list

    @Transactional(readOnly = true)
    public PageView<HandoverListItem> list(int page, int size) {
        var pageable = Paging.of(page, size, Sort.unsorted());
        return Paging.view(handovers.findAllByOrderByVerifiedAtDescIdDesc(pageable), h -> new HandoverListItem(
                h.getHandoverReference(), h.getPickup().getReference(), h.getCustomer().getName(),
                h.getCollector().getName(), h.getPickup().getLocality(), h.getVerifiedAt(), h.getCollectedAt(),
                photos.existsByHandoverId(h.getId()), h.getPickup().isDemo()));
    }

    // ------------------------------------------------------------------ helpers

    private HandoverView toView(Handover h, AppUser viewer) {
        boolean admin = viewer.getRole() == Role.ADMIN;
        return new HandoverView(h.getHandoverReference(), h.getVerifiedAt(), h.getCollectedAt(),
                h.getCollector().getName(),
                admin ? h.getCollector().getId() : null,
                admin ? h.getCustomer().getId() : null,
                photos.existsByHandoverId(h.getId()));
    }

    private Pickup load(String reference) {
        String ref = reference == null ? "" : reference.trim().toUpperCase();
        return pickups.findByReference(ref)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No pickup found for that reference."));
    }

    private static boolean isAssignedCollector(AppUser actor, Pickup pickup) {
        return actor.getRole() == Role.COLLECTOR && pickup.getCollector() != null
                && pickup.getCollector().getId().equals(actor.getId());
    }

    /** Records a refused attempt in the audit log and returns the matching error to send back. */
    private ApiException refuse(AppUser actor, Pickup pickup, HttpStatus status, String auditMessage,
                                String userMessage, Map<String, String> fieldErrors) {
        audit(actor, pickup, false, auditMessage);
        return fieldErrors.isEmpty() ? new ApiException(status, userMessage)
                : new ApiException(status, userMessage, fieldErrors);
    }

    private void audit(AppUser actor, Pickup pickup, boolean success, String message) {
        audits.save(new AuditLog(actor.getId(), actor.getName(), actor.getRole().name(), pickup.getReference(),
                success, message, pickup.isDemo()));
    }

    private String newUniqueReference() {
        while (true) {
            String ref = HandoverRules.newReference(random);
            if (!handovers.existsByHandoverReference(ref)) {
                return ref;
            }
        }
    }
}
