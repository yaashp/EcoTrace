package com.ecotrace.service;

import com.ecotrace.domain.AppUser;
import com.ecotrace.domain.AuditLog;
import com.ecotrace.domain.Handover;
import com.ecotrace.domain.Catalog;
import com.ecotrace.domain.Outcome;
import com.ecotrace.domain.Partner;
import com.ecotrace.domain.PartnerType;
import com.ecotrace.domain.Pickup;
import com.ecotrace.domain.PickupEvent;
import com.ecotrace.domain.PickupItem;
import com.ecotrace.domain.PickupStatus;
import com.ecotrace.domain.Role;
import com.ecotrace.domain.TransitionPolicy;
import com.ecotrace.dto.Dtos.ActionView;
import com.ecotrace.dto.Dtos.HandoverCodeView;
import com.ecotrace.dto.Dtos.HandoverView;
import com.ecotrace.dto.Dtos.BookingItem;
import com.ecotrace.dto.Dtos.BookingRequest;
import com.ecotrace.dto.Dtos.Contact;
import com.ecotrace.dto.Dtos.ItemBrief;
import com.ecotrace.dto.Dtos.ItemView;
import com.ecotrace.dto.Dtos.PageView;
import com.ecotrace.dto.Dtos.PickupDetail;
import com.ecotrace.dto.Dtos.PickupSummary;
import com.ecotrace.dto.Dtos.PublicEvent;
import com.ecotrace.dto.Dtos.PublicTrack;
import com.ecotrace.dto.Dtos.SlotView;
import com.ecotrace.dto.Dtos.TimelineEvent;
import com.ecotrace.dto.Dtos.TransitionRequest;
import com.ecotrace.repo.AuditLogRepository;
import com.ecotrace.repo.PartnerRepository;
import com.ecotrace.repo.PickupEventRepository;
import com.ecotrace.repo.PickupRepository;
import com.ecotrace.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Booking, tracking and every status change. All custody rules live here so they cannot be bypassed:
 * role, ownership or assignment, the transition table, and the evidence a step requires.
 */
@Service
public class PickupService {

    private static final Logger log = LoggerFactory.getLogger(PickupService.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String FIELD_ERROR_MESSAGE = "Please correct the highlighted fields.";

    private final PickupRepository pickups;
    private final PickupEventRepository events;
    private final UserRepository users;
    private final PartnerRepository partners;
    private final AuditLogRepository audits;
    private final NotificationService notifications;
    private final HandoverService handovers;
    private final SecureRandom random = new SecureRandom();

    @Value("${ecotrace.slot-capacity:8}")
    private int slotCapacity;

    @Value("${ecotrace.max-days-ahead:60}")
    private int maxDaysAhead;

    public PickupService(PickupRepository pickups, PickupEventRepository events, UserRepository users,
                         PartnerRepository partners, AuditLogRepository audits, NotificationService notifications,
                         HandoverService handovers) {
        this.pickups = pickups;
        this.events = events;
        this.users = users;
        this.partners = partners;
        this.audits = audits;
        this.notifications = notifications;
        this.handovers = handovers;
    }

    // ------------------------------------------------------------------ booking

    @Transactional(readOnly = true)
    public List<SlotView> slots(LocalDate date) {
        List<SlotView> result = new ArrayList<>();
        for (String slot : Catalog.SLOTS) {
            long used = pickups.countByPickupDateAndTimeSlotAndStatusNot(date, slot, PickupStatus.CANCELLED);
            int remaining = (int) Math.max(0, slotCapacity - used);
            result.add(new SlotView(slot, remaining > 0, remaining));
        }
        return result;
    }

    @Transactional
    public PickupDetail book(AppUser customer, BookingRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        List<BookingItem> items = req.items();
        for (int i = 0; i < items.size(); i++) {
            if (!Catalog.CATEGORIES.contains(items.get(i).category())) {
                errors.put("items[" + i + "].category", "Choose one of the listed categories");
            }
            if (!Catalog.CONDITIONS.contains(items.get(i).condition())) {
                errors.put("items[" + i + "].condition", "Choose Working, Repairable or Non-functional");
            }
        }
        if (!Catalog.SLOTS.contains(req.timeSlot())) {
            errors.put("timeSlot", "Choose one of the listed time slots");
        }
        if (req.pickupDate().isAfter(LocalDate.now().plusDays(maxDaysAhead))) {
            errors.put("pickupDate", "Pickups can be booked up to " + maxDaysAhead + " days ahead");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, FIELD_ERROR_MESSAGE, errors);
        }
        long used = pickups.countByPickupDateAndTimeSlotAndStatusNot(req.pickupDate(), req.timeSlot(), PickupStatus.CANCELLED);
        if (used >= slotCapacity) {
            throw new ApiException(HttpStatus.CONFLICT, "That time slot is full. Choose another slot or date.",
                    Map.of("timeSlot", "This slot is full on the chosen date"));
        }

        AppUser owner = users.findById(customer.getId()).orElseThrow();
        Pickup pickup = new Pickup(newReference(), owner);
        pickup.setAddress(req.address().trim());
        pickup.setCity(req.city().trim());
        pickup.setLocality(req.locality().trim());
        pickup.setContactPhone(req.contactPhone());
        pickup.setPickupDate(req.pickupDate());
        pickup.setTimeSlot(req.timeSlot());
        pickup.setInstructions(blankToNull(req.instructions()));
        pickup.setEstimatedWeightKg(req.estimatedWeightKg());
        for (BookingItem item : items) {
            pickup.addItem(new PickupItem(item.category(), item.quantity(), item.condition(), blankToNull(item.details())));
        }
        pickups.save(pickup);
        events.save(new PickupEvent(pickup, null, PickupStatus.REQUESTED, owner.getId(), owner.getName(),
                Role.CUSTOMER.name(), null, "Pickup requested", Instant.now()));
        audit(owner, pickup.getReference(), true, "Pickup booked", pickup.isDemo());

        notifications.notify(owner, "BOOKING_CONFIRMED", "Booking received",
                "Pickup " + pickup.getReference() + " is booked for " + pickup.getPickupDate() + ", " + pickup.getTimeSlot()
                        + ". An administrator will confirm it and assign a collector.", pickup.getReference());
        notifications.notifyRole(Role.ADMIN, "NEW_PICKUP", "New pickup requested",
                "Pickup " + pickup.getReference() + " in " + pickup.getLocality() + " needs confirmation.", pickup.getReference());
        log.info("Pickup {} booked", pickup.getReference());
        return toDetail(pickup, owner);
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public PageView<PickupSummary> list(AppUser actor, String status, String q, int page, int size) {
        Set<PickupStatus> statuses = parseStatusFilter(status);
        String like = Paging.likePattern(q);
        var pageable = Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Pickup> result;
        switch (actor.getRole()) {
            case ADMIN -> result = pickups.findAllScoped(statuses, like, pageable);
            case CUSTOMER -> result = pickups.findForCustomer(actor.getId(), statuses, like, pageable);
            case COLLECTOR -> result = pickups.findForCollector(actor.getId(), statuses, like, pageable);
            case RECYCLER -> {
                if (actor.getPartner() == null) {
                    return new PageView<>(List.of(), 0, pageable.getPageSize(), 0, 0);
                }
                result = pickups.findForPartner(actor.getPartner().getId(), statuses, like, pageable);
            }
            default -> throw new ApiException(HttpStatus.FORBIDDEN, "Your account is not allowed to do this.");
        }
        boolean showCollector = actor.getRole() != Role.RECYCLER;
        return Paging.view(result, p -> toSummary(p, showCollector));
    }

    @Transactional(readOnly = true)
    public PickupDetail detail(AppUser actor, String reference) {
        Pickup pickup = find(reference);
        if (!canView(actor, pickup)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Pickup not found.");
        }
        return toDetail(pickup, actor);
    }

    /** Public tracking: no names, phone numbers, addresses or free-text notes. */
    @Transactional(readOnly = true)
    public PublicTrack track(String reference) {
        Pickup p = find(reference);
        List<PublicEvent> timeline = events.findByPickupIdOrderByOccurredAtAscIdAsc(p.getId()).stream()
                .map(e -> new PublicEvent(e.getToStatus().name(), e.getToStatus().label(),
                        Role.publicLabel(e.getActorRole()), e.getDetail(), e.getOccurredAt()))
                .toList();
        List<ItemBrief> items = p.getItems().stream().map(i -> new ItemBrief(i.getCategory(), i.getQuantity())).toList();
        return new PublicTrack(p.getReference(), p.getStatus().name(), p.getStatus().label(), p.getStatus().isException(),
                p.getCreatedAt(), p.getCity(), items, nextExpected(p.getStatus()),
                p.getFacility() == null ? null : p.getFacility().getName(),
                p.getPartner() == null ? null : p.getPartner().getName(),
                p.getOutcome() == null ? null : p.getOutcome().label(),
                p.getVerifiedWeightKg(), p.getProcessingReference(), p.isDemo(), timeline,
                handovers.findFor(p).map(Handover::getHandoverReference).orElse(null),
                handovers.findFor(p).map(Handover::getVerifiedAt).orElse(null));
    }

    // ------------------------------------------------------------------ status changes

    /**
     * Applies one status change. Check order: visibility, role, transition table, required details.
     * Blocked attempts are written to the audit log and the transaction still commits (noRollbackFor),
     * so a rejected attempt is never lost.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public PickupDetail transition(AppUser actor, String reference, TransitionRequest req) {
        Pickup pickup = find(reference);
        if (!canView(actor, pickup)) {
            audit(actor, pickup.getReference(), false, "Blocked: pickup is not accessible to this account", pickup.isDemo());
            throw new ApiException(HttpStatus.NOT_FOUND, "Pickup not found.");
        }
        PickupStatus target = parseStatus(req.status());
        PickupStatus from = pickup.getStatus();
        String attempt = from.label() + " to " + target.label();

        if (!TransitionPolicy.mayRecord(actor.getRole(), target)) {
            throw deny(actor, pickup, HttpStatus.FORBIDDEN, "Blocked (" + attempt + "): role not permitted to record that status.",
                    "Your role is not allowed to record that status.", null);
        }
        if (!from.canMoveTo(target)) {
            throw deny(actor, pickup, HttpStatus.CONFLICT, "Blocked (" + attempt + "): not an allowed transition.",
                    "That status change is not allowed from \"" + from.label() + "\".", null);
        }

        if (target == PickupStatus.COLLECTED && !handovers.isVerified(pickup)) {
            throw deny(actor, pickup, HttpStatus.CONFLICT, "Blocked (" + attempt + "): no verified handover.",
                    "Verify the customer's collection code before marking this pickup as collected.", null);
        }
        if (target == PickupStatus.COLLECTED && !handovers.hasPhoto(pickup)) {
            throw deny(actor, pickup, HttpStatus.CONFLICT, "Blocked (" + attempt + "): mandatory geotagged photo missing.",
                    "Upload the required photo with its automatic date, time and GPS location before marking this pickup as collected.", null);
        }

        String note = blankToNull(req.note());
        Map<String, String> errors = new LinkedHashMap<>();
        AppUser collector = null;
        Partner facility = null;
        Partner partner = null;
        Outcome outcome = null;
        switch (target) {
            case CONFIRMED -> {
                collector = req.collectorId() == null ? null : users.findById(req.collectorId()).orElse(null);
                if (collector == null || collector.getRole() != Role.COLLECTOR || !collector.isActive()) {
                    errors.put("collectorId", "Choose an active collector");
                }
            }
            case AT_FACILITY -> {
                facility = req.facilityId() == null ? null : partners.findById(req.facilityId()).orElse(null);
                if (facility == null || !facility.isActive() || facility.getType() != PartnerType.COLLECTION_FACILITY) {
                    errors.put("facilityId", "Choose a collection facility");
                }
            }
            case TRANSFERRED -> {
                partner = req.partnerId() == null ? null : partners.findById(req.partnerId()).orElse(null);
                if (partner == null || !partner.isActive() || partner.getType() != PartnerType.RECYCLER) {
                    errors.put("partnerId", "Choose a recycling partner");
                }
            }
            case RECYCLING_CONFIRMED -> {
                outcome = parseOutcome(req.outcome());
                if (outcome == null) {
                    errors.put("outcome", "Choose the processing outcome");
                }
                if (req.weightKg() == null || req.weightKg() <= 0 || req.weightKg() > 5000) {
                    errors.put("weightKg", "Enter the measured weight in kg");
                }
                if (blankToNull(req.processingReference()) == null) {
                    errors.put("processingReference", "Enter the processing reference or certificate number");
                }
            }
            case FAILED_PICKUP, REJECTED -> {
                if (note == null) {
                    errors.put("note", "Enter the reason");
                }
            }
            default -> { }
        }
        if (!errors.isEmpty()) {
            throw deny(actor, pickup, HttpStatus.BAD_REQUEST, "Blocked (" + attempt + "): required details are missing or invalid.",
                    FIELD_ERROR_MESSAGE, errors);
        }

        String partnerName = target == PickupStatus.TRANSFERRED ? partner.getName()
                : pickup.getPartner() == null ? null : pickup.getPartner().getName();
        String detail = eventDetail(target, facility == null ? null : facility.getName(), partnerName,
                outcome, req.processingReference());

        Instant now = Instant.now();
        String handoverRef = null;
        switch (target) {
            case CONFIRMED -> {
                pickup.setCollector(collector);
                pickup.setReason(null);
            }
            case COLLECTED -> handoverRef = handovers.recordCollection(pickup, now);
            case AT_FACILITY -> pickup.setFacility(facility);
            case TRANSFERRED -> {
                pickup.setPartner(partner);
                pickup.setReason(null);
            }
            case RECYCLING_CONFIRMED -> {
                pickup.setOutcome(outcome);
                pickup.setVerifiedWeightKg(Math.round(req.weightKg() * 10.0) / 10.0);
                pickup.setProcessingReference(req.processingReference().trim());
            }
            case FAILED_PICKUP, REJECTED, CANCELLED -> pickup.setReason(note);
            default -> { }
        }
        if (handoverRef != null) {
            detail = detail + ". Handover " + handoverRef;
        }
        pickup.setStatus(target);
        pickup.setUpdatedAt(now);
        pickups.save(pickup);
        events.save(new PickupEvent(pickup, from, target, actor.getId(), actor.getName(), actor.getRole().name(), note, detail, now));
        audit(actor, pickup.getReference(), true, "Recorded " + attempt, pickup.isDemo());
        notifyTransition(pickup, target, actor);
        log.info("Pickup {} moved {} to {}", pickup.getReference(), from, target);
        return toDetail(pickup, actor);
    }

    private void notifyTransition(Pickup p, PickupStatus target, AppUser actor) {
        String ref = p.getReference();
        AppUser customer = p.getCustomer();
        boolean actorIsCustomer = actor.getId().equals(customer.getId());
        String title = target.label();
        String message = "Pickup " + ref + ": " + target.label().toLowerCase() + ".";
        if (!actorIsCustomer) {
            notifications.notify(customer, target.name(), title, message, ref);
        }
        switch (target) {
            case CONFIRMED -> {
                AppUser collector = p.getCollector();
                notifications.notify(collector, "PICKUP_ASSIGNED", "New pickup assigned",
                        "Pickup " + ref + " in " + p.getLocality() + " on " + p.getPickupDate() + ", " + p.getTimeSlot() + ".", ref);
            }
            case TRANSFERRED -> notifications.notifyPartnerRecyclers(p.getPartner(), "INCOMING_CONSIGNMENT",
                    "Incoming consignment", "Pickup " + ref + " was transferred to " + p.getPartner().getName() + ".", ref);
            case RECYCLING_CONFIRMED -> notifications.notifyRole(Role.ADMIN, "AWAITING_CLOSURE",
                    "Processing confirmed", "Pickup " + ref + " is ready to be closed.", ref);
            case FAILED_PICKUP, REJECTED -> notifications.notifyRole(Role.ADMIN, target.name(),
                    title, "Pickup " + ref + " needs attention: " + target.label().toLowerCase() + ".", ref);
            case CANCELLED -> {
                if (p.getCollector() != null && !p.getCollector().getId().equals(actor.getId())) {
                    notifications.notify(p.getCollector(), "PICKUP_CANCELLED", "Pickup cancelled",
                            "Pickup " + ref + " was cancelled.", ref);
                }
                if (actorIsCustomer) {
                    notifications.notifyRole(Role.ADMIN, "PICKUP_CANCELLED", "Pickup cancelled",
                            "The customer cancelled pickup " + ref + ".", ref);
                }
            }
            default -> { }
        }
    }

    private ApiException deny(AppUser actor, Pickup pickup, HttpStatus status, String auditMessage,
                              String userMessage, Map<String, String> fieldErrors) {
        audit(actor, pickup.getReference(), false, auditMessage, pickup.isDemo());
        return fieldErrors == null ? new ApiException(status, userMessage) : new ApiException(status, userMessage, fieldErrors);
    }

    private void audit(AppUser actor, String reference, boolean success, String message, boolean demo) {
        audits.save(new AuditLog(actor.getId(), actor.getName(), actor.getRole().name(), reference, success, message, demo));
    }

    // ------------------------------------------------------------------ mapping

    private boolean canView(AppUser actor, Pickup p) {
        return switch (actor.getRole()) {
            case ADMIN -> true;
            case CUSTOMER -> p.getCustomer().getId().equals(actor.getId());
            case COLLECTOR -> p.getCollector() != null && p.getCollector().getId().equals(actor.getId());
            case RECYCLER -> actor.getPartner() != null && p.getPartner() != null
                    && p.getPartner().getId().equals(actor.getPartner().getId());
        };
    }

    private PickupDetail toDetail(Pickup p, AppUser viewer) {
        Role role = viewer.getRole();
        boolean recycler = role == Role.RECYCLER;
        Contact contact = recycler ? null
                : new Contact(p.getCustomer().getName(), p.getContactPhone(), p.getAddress(), p.getLocality());
        List<TimelineEvent> timeline = events.findByPickupIdOrderByOccurredAtAscIdAsc(p.getId()).stream()
                .map(e -> new TimelineEvent(e.getToStatus().name(), e.getToStatus().label(), e.getActorName(),
                        e.getActorRole(), Role.publicLabel(e.getActorRole()),
                        (!recycler || Role.RECYCLER.name().equals(e.getActorRole())) ? e.getNote() : null,
                        e.getDetail(), e.getOccurredAt()))
                .toList();
        List<ItemView> items = p.getItems().stream()
                .map(i -> new ItemView(i.getCategory(), i.getQuantity(), i.getItemCondition(), i.getDetails())).toList();
        List<ActionView> actions = new ArrayList<>();
        for (PickupStatus s : p.getStatus().allowedNext()) {
            if (s == PickupStatus.COLLECTED && !handovers.isVerified(p)) {
                continue;
            }
            if (TransitionPolicy.mayRecord(role, s) && canView(viewer, p)) {
                actions.add(new ActionView(s.name(), actionLabel(p.getStatus(), s), fieldsFor(s)));
            }
        }
        AppUser collector = p.getCollector();
        HandoverView handover = handovers.viewFor(p, viewer);
        HandoverCodeView handoverCode = handovers.codeStatusFor(p, viewer);
        return new PickupDetail(p.getReference(), p.getStatus().name(), p.getStatus().label(), p.getStatus().isException(),
                p.getCreatedAt(), p.getPickupDate(), p.getTimeSlot(), p.getCity(), items,
                recycler ? null : p.getInstructions(), p.getEstimatedWeightKg(), p.getVerifiedWeightKg(),
                p.getOutcome() == null ? null : p.getOutcome().label(), p.getProcessingReference(), p.getReason(),
                nextExpected(p.getStatus()),
                recycler || collector == null ? null : collector.getName(),
                recycler || collector == null ? null : collector.getId(),
                p.getFacility() == null ? null : p.getFacility().getName(),
                p.getPartner() == null ? null : p.getPartner().getName(),
                contact, p.isDemo(), timeline, actions, handover, handoverCode,
                handovers.canIssueCode(p, viewer), handovers.canVerify(p, viewer), handovers.canAttachPhoto(p, viewer));
    }

    private PickupSummary toSummary(Pickup p, boolean showCollector) {
        String items = p.getItems().stream().limit(3)
                .map(i -> i.getQuantity() + " x " + i.getCategory()).collect(Collectors.joining(", "));
        if (p.getItems().size() > 3) {
            items += " and " + (p.getItems().size() - 3) + " more";
        }
        boolean open = p.getStatus() == PickupStatus.REQUESTED || p.getStatus() == PickupStatus.CONFIRMED;
        return new PickupSummary(p.getReference(), p.getStatus().name(), p.getStatus().label(), p.getStatus().isException(),
                p.getCreatedAt(), p.getPickupDate(), p.getTimeSlot(), p.getLocality(), items,
                showCollector && p.getCollector() != null ? p.getCollector().getName() : null,
                open && p.getPickupDate().isBefore(LocalDate.now()), p.isDemo());
    }

    /** Text that is safe to show on the public tracking page for each kind of step. */
    static String eventDetail(PickupStatus target, String facilityName, String partnerName, Outcome outcome, String processingRef) {
        return switch (target) {
            case REQUESTED -> "Pickup requested";
            case CONFIRMED -> "Collector assigned";
            case COLLECTED -> "Collected by the assigned collector";
            case AT_FACILITY -> "Facility: " + facilityName;
            case TRANSFERRED -> "Recycling partner: " + partnerName;
            case RECEIVED_BY_PARTNER -> "Received by " + partnerName;
            case PROCESSING -> "Processing started";
            case RECYCLING_CONFIRMED -> "Outcome: " + outcome.label() + ". Processing reference " + processingRef.trim();
            case COMPLETED -> "Case closed";
            case FAILED_PICKUP -> "Collection could not be completed";
            case REJECTED -> "Consignment rejected by the recycling partner";
            case CANCELLED -> "Pickup cancelled";
        };
    }

    private static String actionLabel(PickupStatus from, PickupStatus to) {
        return switch (to) {
            case CONFIRMED -> from == PickupStatus.FAILED_PICKUP ? "Reschedule with a collector" : "Confirm and assign a collector";
            case COLLECTED -> "Mark as collected";
            case AT_FACILITY -> "Record arrival at a collection facility";
            case TRANSFERRED -> "Record transfer to a recycling partner";
            case RECEIVED_BY_PARTNER -> "Confirm receipt";
            case PROCESSING -> "Start processing";
            case RECYCLING_CONFIRMED -> "Confirm processing outcome";
            case COMPLETED -> "Close case";
            case FAILED_PICKUP -> "Report a failed pickup";
            case REJECTED -> "Reject consignment";
            case CANCELLED -> "Cancel pickup";
            case REQUESTED -> "Request pickup";
        };
    }

    private static List<String> fieldsFor(PickupStatus to) {
        return switch (to) {
            case CONFIRMED -> List.of("collectorId", "note");
            case AT_FACILITY -> List.of("facilityId", "note");
            case TRANSFERRED -> List.of("partnerId", "note");
            case RECYCLING_CONFIRMED -> List.of("outcome", "weightKg", "processingReference", "note");
            case FAILED_PICKUP, REJECTED -> List.of("reason");
            default -> List.of("note");
        };
    }

    static String nextExpected(PickupStatus s) {
        return switch (s) {
            case REQUESTED -> "Confirmation and collector assignment";
            case CONFIRMED -> "Collection on the booked date";
            case COLLECTED -> "Handover to a collection facility";
            case AT_FACILITY -> "Transfer to a recycling partner";
            case TRANSFERRED -> "Receipt confirmation by the recycling partner";
            case RECEIVED_BY_PARTNER -> "Processing by the recycling partner";
            case PROCESSING -> "Processing outcome confirmed by the recycling partner";
            case RECYCLING_CONFIRMED -> "Case closure by an administrator";
            case FAILED_PICKUP -> "Rescheduling by an administrator";
            case REJECTED -> "An administrator arranges handling by another partner";
            case COMPLETED, CANCELLED -> null;
        };
    }

    // ------------------------------------------------------------------ helpers

    private Pickup find(String reference) {
        String ref = reference == null ? "" : reference.trim().toUpperCase();
        return pickups.findByReference(ref).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No pickup found for that reference."));
    }

    static PickupStatus parseStatus(String raw) {
        try {
            return PickupStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown status: " + raw);
        }
    }

    private static Outcome parseOutcome(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Outcome.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Accepts ALL (or blank), ACTIVE, CLOSED or a single status name. */
    static Set<PickupStatus> parseStatusFilter(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("ALL")) {
            return EnumSet.allOf(PickupStatus.class);
        }
        if (raw.equalsIgnoreCase("ACTIVE")) {
            Set<PickupStatus> active = EnumSet.allOf(PickupStatus.class);
            active.removeAll(EnumSet.of(PickupStatus.COMPLETED, PickupStatus.CANCELLED, PickupStatus.REJECTED));
            return active;
        }
        if (raw.equalsIgnoreCase("CLOSED")) {
            return EnumSet.of(PickupStatus.COMPLETED, PickupStatus.CANCELLED, PickupStatus.REJECTED);
        }
        return EnumSet.of(parseStatus(raw));
    }

    private String newReference() {
        while (true) {
            StringBuilder sb = new StringBuilder("EW-");
            for (int i = 0; i < 8; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String ref = sb.toString();
            if (!pickups.existsByReference(ref)) {
                return ref;
            }
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
