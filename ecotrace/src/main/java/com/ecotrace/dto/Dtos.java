package com.ecotrace.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Request and response shapes for the REST API. */
public final class Dtos {

    private Dtos() {
    }

    public static final String PHONE_REGEX = "^[6-9][0-9]{9}$";
    public static final String PHONE_MESSAGE = "Enter a 10-digit Indian mobile number";

    // ---------- requests ----------

    public record RegisterRequest(
            @NotBlank(message = "Enter your name") @Size(max = 80) String name,
            @NotBlank(message = "Enter your email address") @Email(message = "Enter a valid email address") @Size(max = 120) String email,
            @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @NotBlank(message = "Choose a password") @Size(max = 72, message = "Use at most 72 characters") String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Enter your email address") String email,
            @NotBlank(message = "Enter your password") String password) {
    }

    public record ProfileRequest(
            @NotBlank(message = "Enter your name") @Size(max = 80) String name,
            @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone) {
    }

    public record PasswordChangeRequest(
            @NotBlank(message = "Enter your current password") String currentPassword,
            @NotBlank(message = "Choose a new password") @Size(max = 72, message = "Use at most 72 characters") String newPassword) {
    }

    public record BookingItem(
            @NotBlank(message = "Choose a category") String category,
            @Min(value = 1, message = "Quantity must be at least 1") @Max(value = 500, message = "Quantity is too large") int quantity,
            @NotBlank(message = "Choose a condition") String condition,
            @Size(max = 200, message = "Use at most 200 characters") String details) {
    }

    public record BookingRequest(
            @NotEmpty(message = "Add at least one item") @Size(max = 10, message = "Add at most 10 items") @Valid List<BookingItem> items,
            @NotBlank(message = "Enter the pickup address") @Size(max = 200) String address,
            @NotBlank(message = "Enter the city") @Size(max = 60) String city,
            @NotBlank(message = "Enter the locality") @Size(max = 60) String locality,
            @NotBlank(message = "Enter a contact number") @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String contactPhone,
            @NotNull(message = "Choose a pickup date") @FutureOrPresent(message = "Choose today or a later date") LocalDate pickupDate,
            @NotBlank(message = "Choose a time slot") String timeSlot,
            @Size(max = 500, message = "Use at most 500 characters") String instructions,
            @Positive(message = "Weight must be greater than zero") @DecimalMax(value = "2000.0", message = "Weight is too large") Double estimatedWeightKg) {
    }

    /** One request shape for every status change. Which optional fields apply depends on the target status. */
    public record TransitionRequest(
            @NotBlank(message = "Choose a status") String status,
            @Size(max = 450, message = "Use at most 450 characters") String note,
            Long collectorId,
            Long facilityId,
            Long partnerId,
            String outcome,
            Double weightKg,
            @Size(max = 100, message = "Use at most 100 characters") String processingReference) {
    }

    public record CreateUserRequest(
            @NotBlank(message = "Enter a name") @Size(max = 80) String name,
            @NotBlank(message = "Enter an email address") @Email(message = "Enter a valid email address") @Size(max = 120) String email,
            @Pattern(regexp = PHONE_REGEX, message = PHONE_MESSAGE) String phone,
            @NotBlank(message = "Choose a password") @Size(max = 72, message = "Use at most 72 characters") String password,
            @NotBlank(message = "Choose a role") String role,
            Long partnerId) {
    }

    public record ActiveRequest(boolean active) {
    }

    public record PartnerRequest(
            @NotBlank(message = "Enter a name") @Size(max = 120) String name,
            @NotBlank(message = "Choose a type") String type,
            @NotBlank(message = "Enter an address") @Size(max = 200) String address,
            @NotBlank(message = "Enter a city") @Size(max = 60) String city,
            @NotBlank(message = "Enter a locality") @Size(max = 60) String locality,
            @Size(max = 300) String serviceArea,
            @NotEmpty(message = "Choose at least one accepted category") List<String> acceptedCategories,
            @Email(message = "Enter a valid email address") @Size(max = 120) String contactEmail,
            @Size(max = 20) String contactPhone,
            List<String> services,
            String verification,
            @Size(max = 300) String verificationNote,
            Double lat,
            Double lng,
            Boolean active) {
    }

    /** The 6-digit collection code the customer shared with the collector. */
    public record VerifyHandoverRequest(
            @NotBlank(message = "Enter the 6-digit code") @Size(max = 12, message = "Enter the 6-digit code") String code) {
    }

    public record AssistantRequest(@NotBlank(message = "Describe the item") @Size(max = 300) String description) {
    }

    // ---------- responses ----------

    public record UserView(Long id, String name, String email, String phone, String role, String roleLabel,
                           Long partnerId, String partnerName, boolean active, boolean demo, Instant createdAt) {
    }

    public record DemoAccount(String email, String roleLabel) {
    }

    public record ConfigView(boolean demoMode, String demoPassword, List<DemoAccount> demoAccounts) {
    }

    public record MetaView(List<String> categories, List<String> conditions, List<String> slots,
                           Map<String, String> outcomes, Map<String, String> statuses) {
    }

    public record SlotView(String slot, boolean available, int remaining) {
    }

    public record ItemView(String category, int quantity, String condition, String details) {
    }

    public record ItemBrief(String category, int quantity) {
    }

    /** Full chain-of-custody entry for people allowed to see the whole record. */
    public record TimelineEvent(String status, String statusLabel, String actorName, String actorRole,
                                String roleLabel, String note, String detail, Instant occurredAt) {
    }

    /** Public chain-of-custody entry: no individual names and no free-text notes. */
    public record PublicEvent(String status, String statusLabel, String roleLabel, String detail, Instant occurredAt) {
    }

    public record PublicTrack(String reference, String status, String statusLabel, boolean exception,
                              Instant createdAt, String city, List<ItemBrief> items, String nextExpected,
                              String facilityName, String partnerName, String outcomeLabel,
                              Double verifiedWeightKg, String processingReference, boolean demo,
                              List<PublicEvent> timeline, String handoverReference, Instant handoverVerifiedAt) {
    }

    /** What the current user may do next. fields lists the inputs the form must collect. */
    public record ActionView(String status, String label, List<String> fields) {
    }

    public record Contact(String name, String phone, String address, String locality) {
    }

    /** Returned once, when a customer generates a collection code. The code is never returned again. */
    public record HandoverCodeIssued(String code, Instant expiresAt, int maxAttempts) {
    }

    /** Status of the current code for the customer. Never contains the code itself. */
    public record HandoverCodeView(boolean active, Instant expiresAt, int attemptsLeft) {
    }

    /**
     * A verified handover. collectorId and customerId are included for administrators only; other viewers
     * get the reference, times, collector name and whether a photo exists.
     */
    public record HandoverView(String handoverReference, Instant verifiedAt, Instant collectedAt,
                               String collectorName, Long collectorId, Long customerId, boolean hasPhoto) {
    }

    public record HandoverListItem(String handoverReference, String pickupReference, String customerName,
                                   String collectorName, String locality, Instant verifiedAt, Instant collectedAt,
                                   boolean hasPhoto, boolean demo) {
    }

    public record PickupDetail(String reference, String status, String statusLabel, boolean exception,
                               Instant createdAt, LocalDate pickupDate, String timeSlot, String city,
                               List<ItemView> items, String instructions, Double estimatedWeightKg,
                               Double verifiedWeightKg, String outcomeLabel, String processingReference,
                               String reason, String nextExpected, String collectorName, Long collectorId,
                               String facilityName, String partnerName, Contact contact, boolean demo,
                               List<TimelineEvent> timeline, List<ActionView> allowedActions,
                               HandoverView handover, HandoverCodeView handoverCode,
                               boolean canGenerateHandoverCode, boolean canVerifyHandover, boolean canAttachPhoto) {
    }

    public record PickupSummary(String reference, String status, String statusLabel, boolean exception,
                                Instant createdAt, LocalDate pickupDate, String timeSlot, String locality,
                                String itemsSummary, String collectorName, boolean overdue, boolean demo) {
    }

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    public record PartnerView(Long id, String name, String type, String typeLabel, String address, String city,
                              String locality, String serviceArea, List<String> acceptedCategories,
                              String contactEmail, String contactPhone, List<String> services,
                              String verification, String verificationLabel, String verificationNote,
                              Double lat, Double lng, boolean active, boolean demo) {
    }

    public record NotificationView(Long id, String type, String title, String message,
                                   String pickupReference, Instant createdAt, boolean read) {
    }

    public record AuditView(Long id, String pickupReference, String actorName, String actorRole,
                            boolean success, String message, Instant occurredAt) {
    }

    public record AssistantResponse(String suggestedCategory, String matchedKeyword, String guidance,
                                    String method, String notice) {
    }

    public record Metrics(
            int totalBookings, int pendingPickups, int completedCollections, int awaitingProcessing,
            int recyclingConfirmed, int failedPickups, int rejected, int cancelled, int overdue,
            double estimatedWeightKg, double verifiedWeightKg, double avgCollectionHours,
            boolean includesDemoData,
            Map<String, Long> byStatus, Map<String, Long> byMonth, Map<String, Long> byCategory,
            Map<String, Long> byLocality) {
    }
}
