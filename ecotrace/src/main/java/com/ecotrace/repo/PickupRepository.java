package com.ecotrace.repo;

import com.ecotrace.domain.Pickup;
import com.ecotrace.domain.PickupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PickupRepository extends JpaRepository<Pickup, Long> {

    Optional<Pickup> findByReference(String reference);

    boolean existsByReference(String reference);

    boolean existsByDemoTrue();

    long countByPickupDateAndTimeSlotAndStatusNot(LocalDate pickupDate, String timeSlot, PickupStatus status);

    // The four list queries differ only in how the caller is scoped. Both filters always receive a value
    // (all statuses, or "%"), which avoids null-parameter handling in JPQL.

    @Query("select p from Pickup p where p.status in :statuses and lower(p.reference) like :q")
    Page<Pickup> findAllScoped(@Param("statuses") Collection<PickupStatus> statuses, @Param("q") String q, Pageable pageable);

    @Query("select p from Pickup p where p.customer.id = :userId and p.status in :statuses and lower(p.reference) like :q")
    Page<Pickup> findForCustomer(@Param("userId") Long userId, @Param("statuses") Collection<PickupStatus> statuses,
                                 @Param("q") String q, Pageable pageable);

    @Query("select p from Pickup p where p.collector.id = :userId and p.status in :statuses and lower(p.reference) like :q")
    Page<Pickup> findForCollector(@Param("userId") Long userId, @Param("statuses") Collection<PickupStatus> statuses,
                                  @Param("q") String q, Pageable pageable);

    @Query("select p from Pickup p where p.partner.id = :partnerId and p.status in :statuses and lower(p.reference) like :q")
    Page<Pickup> findForPartner(@Param("partnerId") Long partnerId, @Param("statuses") Collection<PickupStatus> statuses,
                                @Param("q") String q, Pageable pageable);

    // ----- analytics -----

    @Query("select p from Pickup p where p.createdAt >= :from and p.createdAt < :to and (:includeDemo = true or p.demo = false)")
    List<Pickup> findForMetrics(@Param("from") Instant from, @Param("to") Instant to, @Param("includeDemo") boolean includeDemo);

    @Query("select i.category, sum(i.quantity) from PickupItem i join i.pickup p "
            + "where p.createdAt >= :from and p.createdAt < :to and (:includeDemo = true or p.demo = false) group by i.category")
    List<Object[]> itemsByCategory(@Param("from") Instant from, @Param("to") Instant to, @Param("includeDemo") boolean includeDemo);

    // ----- demo reset only -----

    @Modifying
    @Query("delete from PickupItem i where i.pickup.id in (select p.id from Pickup p where p.demo = true)")
    void deleteDemoItems();

    @Modifying
    @Query("delete from Pickup p where p.demo = true")
    void deleteDemoPickups();
}
