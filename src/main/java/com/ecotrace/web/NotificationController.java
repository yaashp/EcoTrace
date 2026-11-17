package com.ecotrace.web;

import com.ecotrace.domain.AppUser;
import com.ecotrace.dto.Dtos.NotificationView;
import com.ecotrace.dto.Dtos.PageView;
import com.ecotrace.service.AuthService;
import com.ecotrace.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final AuthService auth;
    private final NotificationService notifications;

    public NotificationController(AuthService auth, NotificationService notifications) {
        this.auth = auth;
        this.notifications = notifications;
    }

    @GetMapping
    public PageView<NotificationView> list(HttpServletRequest http,
                                           @RequestParam(defaultValue = "false") boolean unreadOnly,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        AppUser user = auth.requireAny(http);
        return notifications.list(user, unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(HttpServletRequest http) {
        AppUser user = auth.requireAny(http);
        return Map.of("count", notifications.unreadCount(user));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(HttpServletRequest http, @PathVariable Long id) {
        notifications.markRead(auth.requireAny(http), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(HttpServletRequest http) {
        notifications.markAllRead(auth.requireAny(http));
    }
}
