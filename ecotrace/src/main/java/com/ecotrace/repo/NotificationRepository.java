package com.ecotrace.repo;

import com.ecotrace.domain.AppUser;
import com.ecotrace.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserOrderByCreatedAtDescIdDesc(AppUser user, Pageable pageable);

    Page<Notification> findByUserAndReadAtIsNullOrderByCreatedAtDescIdDesc(AppUser user, Pageable pageable);

    long countByUserAndReadAtIsNull(AppUser user);

    Optional<Notification> findByIdAndUser(Long id, AppUser user);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    void markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from Notification n where n.pickupReference in (select p.reference from Pickup p where p.demo = true)")
    void deleteForDemoPickups();
}
