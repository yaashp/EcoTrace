package com.ecotrace.service;

import com.ecotrace.domain.AppUser;
import com.ecotrace.domain.Notification;
import com.ecotrace.domain.Partner;
import com.ecotrace.domain.Role;
import com.ecotrace.dto.Dtos.NotificationView;
import com.ecotrace.dto.Dtos.PageView;
import com.ecotrace.repo.NotificationRepository;
import com.ecotrace.repo.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * In-app notifications only. Nothing is sent by email or SMS: that needs a provider and credentials and
 * is listed as a future integration.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserRepository users;

    public NotificationService(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    @Transactional
    public void notify(AppUser user, String type, String title, String message, String reference) {
        notifications.save(new Notification(user, type, title, message, reference, Instant.now()));
    }

    @Transactional
    public void notifyRole(Role role, String type, String title, String message, String reference) {
        for (AppUser user : users.findByRoleAndActiveTrue(role)) {
            notify(user, type, title, message, reference);
        }
    }

    @Transactional
    public void notifyPartnerRecyclers(Partner partner, String type, String title, String message, String reference) {
        if (partner == null) {
            return;
        }
        for (AppUser user : users.findByPartnerAndRoleAndActiveTrue(partner, Role.RECYCLER)) {
            notify(user, type, title, message, reference);
        }
    }

    @Transactional(readOnly = true)
    public PageView<NotificationView> list(AppUser user, boolean unreadOnly, int page, int size) {
        var pageable = Paging.of(page, size, Sort.unsorted());
        Page<Notification> result = unreadOnly
                ? notifications.findByUserAndReadAtIsNullOrderByCreatedAtDescIdDesc(user, pageable)
                : notifications.findByUserOrderByCreatedAtDescIdDesc(user, pageable);
        return Paging.view(result, NotificationService::toView);
    }

    @Transactional(readOnly = true)
    public long unreadCount(AppUser user) {
        return notifications.countByUserAndReadAtIsNull(user);
    }

    @Transactional
    public void markRead(AppUser user, Long id) {
        Notification n = notifications.findByIdAndUser(id, user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notification not found."));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            notifications.save(n);
        }
    }

    @Transactional
    public void markAllRead(AppUser user) {
        notifications.markAllRead(user.getId(), Instant.now());
    }

    private static NotificationView toView(Notification n) {
        return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getPickupReference(), n.getCreatedAt(), n.getReadAt() != null);
    }
}
