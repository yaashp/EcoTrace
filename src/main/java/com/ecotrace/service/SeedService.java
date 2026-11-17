package com.ecotrace.service;

import com.ecotrace.domain.AppUser;
import com.ecotrace.domain.Catalog;
import com.ecotrace.domain.Handover;
import com.ecotrace.domain.Notification;
import com.ecotrace.domain.Outcome;
import com.ecotrace.domain.Partner;
import com.ecotrace.domain.PartnerType;
import com.ecotrace.domain.Pickup;
import com.ecotrace.domain.PickupEvent;
import com.ecotrace.domain.PickupItem;
import com.ecotrace.domain.PickupStatus;
import com.ecotrace.domain.Role;
import com.ecotrace.domain.Verification;
import com.ecotrace.dto.Dtos.ConfigView;
import com.ecotrace.dto.Dtos.DemoAccount;
import com.ecotrace.repo.AuditLogRepository;
import com.ecotrace.repo.HandoverCodeRepository;
import com.ecotrace.repo.HandoverPhotoRepository;
import com.ecotrace.repo.HandoverRepository;
import com.ecotrace.repo.NotificationRepository;
import com.ecotrace.repo.PartnerRepository;
import com.ecotrace.repo.PickupEventRepository;
import com.ecotrace.repo.PickupRepository;
import com.ecotrace.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the optional first administrator and the clearly labelled demonstration data.
 * Demonstration rows carry demo = true, so they can be reset or excluded from analytics without touching
 * real accounts or real bookings.
 */
@Service
public class SeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);

    private static final List<PickupStatus> MAIN_PATH = List.of(
            PickupStatus.REQUESTED, PickupStatus.CONFIRMED, PickupStatus.COLLECTED, PickupStatus.AT_FACILITY,
            PickupStatus.TRANSFERRED, PickupStatus.RECEIVED_BY_PARTNER, PickupStatus.PROCESSING,
            PickupStatus.RECYCLING_CONFIRMED, PickupStatus.COMPLETED);

    /** Hours after booking at which each step of a path happened. */
    private static final double[] OFFSET_HOURS = {0, 1, 20, 26, 50, 72, 80, 100, 120};

    /** items use the format category|quantity|condition|details */
    private record Spec(String code, int customer, int collector, String locality, int hoursAgo,
                        PickupStatus finalStatus, double kg, Outcome outcome, String... items) {
    }

    private static final List<Spec> SPECS = List.of(
            new Spec("DEMO0001", 0, 0, "Andheri", 3, PickupStatus.REQUESTED, 2.4, null,
                    "Laptop|1|Non-functional|Cracked screen", "Accessories|2|Working|Charger and mouse"),
            new Spec("DEMO0002", 1, 1, "Dadar", 6, PickupStatus.REQUESTED, 0.8, null, "Mobile phone|2|Non-functional|"),
            new Spec("DEMO0003", 0, 0, "Borivali", 30, PickupStatus.CONFIRMED, 7.5, null, "Desktop or monitor|1|Non-functional|Desktop tower"),
            new Spec("DEMO0004", 1, 1, "Thane", 150, PickupStatus.CONFIRMED, 1.2, null, "Battery|3|Non-functional|Old phone batteries"),
            new Spec("DEMO0005", 0, 0, "Powai", 40, PickupStatus.COLLECTED, 4.2, null, "Printer|1|Non-functional|"),
            new Spec("DEMO0006", 1, 1, "Bandra", 60, PickupStatus.AT_FACILITY, 4.6, null, "Laptop|2|Repairable|"),
            new Spec("DEMO0007", 0, 0, "Andheri", 90, PickupStatus.TRANSFERRED, 1.1, null, "Cables|1|Working|Box of cables"),
            new Spec("DEMO0008", 1, 1, "Dadar", 100, PickupStatus.RECEIVED_BY_PARTNER, 0.9, null, "Mobile phone|3|Working|"),
            new Spec("DEMO0009", 0, 0, "Thane", 110, PickupStatus.PROCESSING, 9.0, null, "Desktop or monitor|2|Non-functional|"),
            new Spec("DEMO0010", 1, 1, "Borivali", 150, PickupStatus.RECYCLING_CONFIRMED, 2.2, Outcome.RECYCLED, "Laptop|1|Non-functional|"),
            new Spec("DEMO0011", 0, 0, "Powai", 400, PickupStatus.COMPLETED, 1.1, Outcome.RECYCLED, "Cables|1|Working|"),
            new Spec("DEMO0012", 1, 1, "Bandra", 800, PickupStatus.COMPLETED, 0.9, Outcome.REUSED, "Mobile phone|3|Working|"),
            new Spec("DEMO0013", 0, 0, "Thane", 1500, PickupStatus.COMPLETED, 2.6, Outcome.COMPONENTS_RECOVERED, "Laptop|1|Non-functional|"),
            new Spec("DEMO0014", 1, 1, "Andheri", 200, PickupStatus.REJECTED, 1.8, null, "Battery|1|Non-functional|Swollen battery"),
            new Spec("DEMO0015", 0, 0, "Dadar", 70, PickupStatus.FAILED_PICKUP, 5.2, null, "Printer|1|Non-functional|"),
            new Spec("DEMO0016", 1, 1, "Borivali", 50, PickupStatus.CANCELLED, 3.0, null, "Desktop or monitor|1|Working|"));

    private final UserRepository users;
    private final PartnerRepository partners;
    private final PickupRepository pickups;
    private final PickupEventRepository events;
    private final NotificationRepository notifications;
    private final AuditLogRepository audits;
    private final AuthService auth;
    private final HandoverRepository handovers;
    private final HandoverCodeRepository handoverCodes;
    private final HandoverPhotoRepository handoverPhotos;

    @Value("${ecotrace.demo-mode:true}")
    private boolean demoMode;

    @Value("${ecotrace.demo-password:DemoPass123}")
    private String demoPassword;

    @Value("${ecotrace.bootstrap-admin-email:}")
    private String bootstrapEmail;

    @Value("${ecotrace.bootstrap-admin-password:}")
    private String bootstrapPassword;

    public SeedService(UserRepository users, PartnerRepository partners, PickupRepository pickups,
                       PickupEventRepository events, NotificationRepository notifications,
                       AuditLogRepository audits, AuthService auth, HandoverRepository handovers,
                       HandoverCodeRepository handoverCodes, HandoverPhotoRepository handoverPhotos) {
        this.users = users;
        this.partners = partners;
        this.pickups = pickups;
        this.events = events;
        this.notifications = notifications;
        this.audits = audits;
        this.auth = auth;
        this.handovers = handovers;
        this.handoverCodes = handoverCodes;
        this.handoverPhotos = handoverPhotos;
    }

    public boolean isDemoMode() {
        return demoMode;
    }

    public ConfigView config() {
        if (!demoMode) {
            return new ConfigView(false, null, List.of());
        }
        return new ConfigView(true, demoPassword, List.of(
                new DemoAccount("customer@demo.example", "Customer"),
                new DemoAccount("collector@demo.example", "Collector"),
                new DemoAccount("recycler@demo.example", "Recycler"),
                new DemoAccount("admin@demo.example", "Administrator")));
    }

    // ------------------------------------------------------------------ startup

    /** Creates the first administrator from configuration when no administrator exists yet. */
    @Transactional
    public void bootstrapAdmin() {
        if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank() || users.existsByRole(Role.ADMIN)) {
            return;
        }
        String error = PasswordRules.check(bootstrapPassword);
        if (error != null) {
            log.warn("Bootstrap administrator not created: the configured password is too weak ({})", error);
            return;
        }
        AppUser admin = new AppUser("Administrator", AuthService.normalizeEmail(bootstrapEmail), null,
                auth.hashPassword(bootstrapPassword), Role.ADMIN);
        users.save(admin);
        log.info("Bootstrap administrator created");
    }

    @Transactional
    public void seedDemo() {
        ensureSamplePartners();
        List<AppUser> demoUsers = ensureDemoUsers();
        if (!pickups.existsByDemoTrue()) {
            seedPickups(demoUsers);
            log.info("Demonstration data created");
        }
    }

    /** Removes only demonstration pickups and their history, then recreates them. Real data is untouched. */
    @Transactional
    public void resetDemo() {
        if (!demoMode) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Demo reset is only available in demo mode.");
        }
        notifications.deleteForDemoPickups();
        audits.deleteDemo();
        events.deleteDemoEvents();
        handoverPhotos.deleteForDemoPickups();
        handovers.deleteForDemoPickups();
        handoverCodes.deleteForDemoPickups();
        pickups.deleteDemoItems();
        pickups.deleteDemoPickups();
        seedDemo();
    }

    // ------------------------------------------------------------------ users and partners

    private List<AppUser> ensureDemoUsers() {
        Partner thane = partners.findAllByOrderByNameAsc().stream()
                .filter(p -> p.isDemo() && p.getType() == PartnerType.RECYCLER && p.getLocality().equals("Thane"))
                .findFirst().orElseThrow();
        List<AppUser> result = new ArrayList<>();
        result.add(demoUser("Asha Patil", "customer@demo.example", "9000000001", Role.CUSTOMER, null));
        result.add(demoUser("Rohan Mehta", "customer2@demo.example", "9000000002", Role.CUSTOMER, null));
        result.add(demoUser("Imran Shaikh", "collector@demo.example", "9000000003", Role.COLLECTOR, null));
        result.add(demoUser("Kavya Nair", "collector2@demo.example", "9000000004", Role.COLLECTOR, null));
        result.add(demoUser("Neha Kulkarni", "recycler@demo.example", "9000000005", Role.RECYCLER, thane));
        result.add(demoUser("Platform Admin", "admin@demo.example", "9000000006", Role.ADMIN, null));
        return result;
    }

    private AppUser demoUser(String name, String email, String phone, Role role, Partner partner) {
        return users.findByEmail(email).orElseGet(() -> {
            AppUser u = new AppUser(name, email, phone, auth.hashPassword(demoPassword), role);
            u.setDemo(true);
            u.setPartner(partner);
            return users.save(u);
        });
    }

    private void ensureSamplePartners() {
        if (partners.findAllByOrderByNameAsc().stream().anyMatch(Partner::isDemo)) {
            return;
        }
        String all = String.join(",", Catalog.CATEGORIES);
        samplePartner("Sample Collection Facility, Andheri", PartnerType.COLLECTION_FACILITY, "Andheri",
                "Andheri, Jogeshwari, Vile Parle", "Mobile phone,Laptop,Cables,Accessories,Printer", 19.1197, 72.8468);
        samplePartner("Sample Collection Facility, Dadar", PartnerType.COLLECTION_FACILITY, "Dadar",
                "Dadar, Matunga, Worli", "Mobile phone,Laptop,Desktop or monitor,Cables,Accessories", 19.0178, 72.8478);
        samplePartner("Sample Collection Facility, Borivali", PartnerType.COLLECTION_FACILITY, "Borivali",
                "Borivali, Kandivali, Dahisar", "Mobile phone,Laptop,Cables,Accessories", 19.2307, 72.8567);
        samplePartner("Sample Recycling Partner, Thane", PartnerType.RECYCLER, "Thane",
                "Thane, Mulund, Navi Mumbai", all, 19.2183, 72.9781);
        samplePartner("Sample Recycling Partner, Powai", PartnerType.RECYCLER, "Powai",
                "Powai, Vikhroli, Ghatkopar", "Laptop,Desktop or monitor,Battery,Other electronic device", 19.1176, 72.9060);
    }

    private void samplePartner(String name, PartnerType type, String locality, String serviceArea, String categories,
                               double lat, double lng) {
        Partner p = new Partner(name, type);
        p.setAddress("Sample address in " + locality + ", Mumbai (not a real location)");
        p.setCity("Mumbai");
        p.setLocality(locality);
        p.setServiceArea(serviceArea + " (sample)");
        p.setAcceptedCategories(categories);
        p.setContactEmail("contact@sample-partner.example");
        p.setServices("DROP_OFF");
        p.setVerification(Verification.SAMPLE);
        p.setVerificationNote("Demonstration entry. Not a real facility and not verified.");
        p.setLat(lat);
        p.setLng(lng);
        p.setDemo(true);
        partners.save(p);
    }

    // ------------------------------------------------------------------ pickups

    private void seedPickups(List<AppUser> demoUsers) {
        AppUser[] customers = {demoUsers.get(0), demoUsers.get(1)};
        AppUser[] collectors = {demoUsers.get(2), demoUsers.get(3)};
        AppUser recycler = demoUsers.get(4);
        AppUser admin = demoUsers.get(5);
        List<Partner> all = partners.findAllByOrderByNameAsc();
        Partner facility = all.stream().filter(p -> p.isDemo() && p.getType() == PartnerType.COLLECTION_FACILITY).findFirst().orElseThrow();
        Partner recycling = recycler.getPartner();
        Instant now = Instant.now();

        for (Spec spec : SPECS) {
            AppUser customer = customers[spec.customer()];
            AppUser collector = collectors[spec.collector()];
            List<PickupStatus> path = pathFor(spec.finalStatus());
            Instant created = now.minusSeconds(spec.hoursAgo() * 3600L);

            Pickup p = new Pickup("EW-" + spec.code(), customer);
            p.setCreatedAt(created);
            p.setAddress("Sample address, " + spec.locality() + ", Mumbai");
            p.setCity("Mumbai");
            p.setLocality(spec.locality());
            p.setContactPhone(customer.getPhone());
            p.setPickupDate(created.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1));
            p.setTimeSlot("12:00-15:00");
            p.setEstimatedWeightKg(spec.kg());
            p.setDemo(true);
            for (String raw : spec.items()) {
                String[] f = raw.split("\\|", -1);
                p.addItem(new PickupItem(f[0], Integer.parseInt(f[1]), f[2], f[3].isBlank() ? null : f[3]));
            }
            if (path.contains(PickupStatus.CONFIRMED)) {
                p.setCollector(collector);
            }
            if (path.contains(PickupStatus.AT_FACILITY)) {
                p.setFacility(facility);
            }
            if (path.contains(PickupStatus.TRANSFERRED)) {
                p.setPartner(recycling);
            }
            String reason = reasonFor(spec.finalStatus());
            p.setReason(reason);
            if (path.contains(PickupStatus.RECYCLING_CONFIRMED)) {
                p.setOutcome(spec.outcome());
                p.setVerifiedWeightKg(Math.round(spec.kg() * 0.94 * 10.0) / 10.0);
                p.setProcessingReference("PR-" + spec.code());
            }
            p.setStatus(spec.finalStatus());

            Instant last = created;
            PickupStatus previous = null;
            List<PickupEvent> history = new ArrayList<>();
            for (int k = 0; k < path.size(); k++) {
                PickupStatus status = path.get(k);
                Instant at = created.plusMillis((long) (OFFSET_HOURS[Math.min(k, OFFSET_HOURS.length - 1)] * 3_600_000));
                AppUser actor = switch (status) {
                    case REQUESTED, CANCELLED -> customer;
                    case CONFIRMED, COMPLETED -> admin;
                    case COLLECTED, AT_FACILITY, TRANSFERRED, FAILED_PICKUP -> collector;
                    default -> recycler;
                };
                String note = switch (status) {
                    case FAILED_PICKUP, REJECTED, CANCELLED -> reason;
                    default -> null;
                };
                String detail = PickupService.eventDetail(status, facility.getName(),
                        recycling == null ? null : recycling.getName(), spec.outcome(), p.getProcessingReference());
                history.add(new PickupEvent(p, previous, status, actor.getId(), actor.getName(), actor.getRole().name(), note, detail, at));
                previous = status;
                last = at;
            }
            p.setUpdatedAt(last);
            pickups.save(p);
            history.forEach(events::save);
            if (path.contains(PickupStatus.COLLECTED)) {
                // Demonstration handover: verified one hour after booking, collected at the COLLECTED step.
                Instant verifiedAt = created.plusMillis((long) (OFFSET_HOURS[1] * 3_600_000));
                Instant collectedAt = created.plusMillis((long) (OFFSET_HOURS[2] * 3_600_000));
                Handover handover = new Handover("HO-" + spec.code(), p, customer, collector, verifiedAt);
                handover.markCollected(collectedAt);
                handovers.save(handover);
            }

            notifications.save(new Notification(customer, spec.finalStatus().name(), spec.finalStatus().label(),
                    "Pickup " + p.getReference() + ": " + spec.finalStatus().label().toLowerCase() + ".",
                    p.getReference(), last).markReadIf(spec.hoursAgo() > 48, last));
            if (path.contains(PickupStatus.CONFIRMED) && spec.finalStatus() != PickupStatus.CANCELLED) {
                notifications.save(new Notification(collector, "PICKUP_ASSIGNED", "New pickup assigned",
                        "Pickup " + p.getReference() + " in " + spec.locality() + ".", p.getReference(),
                        created.plusSeconds(3600)).markReadIf(spec.hoursAgo() > 48, last));
            }
            if (spec.finalStatus() == PickupStatus.REQUESTED) {
                notifications.save(new Notification(admin, "NEW_PICKUP", "New pickup requested",
                        "Pickup " + p.getReference() + " in " + spec.locality() + " needs confirmation.",
                        p.getReference(), created));
            }
        }
    }

    private static List<PickupStatus> pathFor(PickupStatus finalStatus) {
        switch (finalStatus) {
            case FAILED_PICKUP:
                return List.of(PickupStatus.REQUESTED, PickupStatus.CONFIRMED, PickupStatus.FAILED_PICKUP);
            case CANCELLED:
                return List.of(PickupStatus.REQUESTED, PickupStatus.CANCELLED);
            case REJECTED:
                return List.of(PickupStatus.REQUESTED, PickupStatus.CONFIRMED, PickupStatus.COLLECTED, PickupStatus.AT_FACILITY,
                        PickupStatus.TRANSFERRED, PickupStatus.RECEIVED_BY_PARTNER, PickupStatus.REJECTED);
            default:
                return MAIN_PATH.subList(0, MAIN_PATH.indexOf(finalStatus) + 1);
        }
    }

    private static String reasonFor(PickupStatus status) {
        return switch (status) {
            case FAILED_PICKUP -> "Nobody was at the address during the booked slot.";
            case REJECTED -> "Swollen battery: unsafe to process at this facility.";
            case CANCELLED -> "Customer changed plans.";
            default -> null;
        };
    }
}
