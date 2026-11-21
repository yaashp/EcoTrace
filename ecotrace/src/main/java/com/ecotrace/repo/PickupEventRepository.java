package com.ecotrace.repo;

import com.ecotrace.domain.PickupEvent;
import com.ecotrace.domain.PickupStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Deliberately NOT a JpaRepository: it can insert and read custody events, never update or delete a
 * single one. The only delete is the demo reset. A production database should additionally forbid
 * UPDATE and DELETE on this table through permissions or triggers.
 */
public interface PickupEventRepository extends Repository<PickupEvent, Long> {

    <S extends PickupEvent> S save(S event);

    List<PickupEvent> findByPickupIdOrderByOccurredAtAscIdAsc(Long pickupId);

    @Query("select p.createdAt, e.occurredAt from PickupEvent e join e.pickup p "
            + "where e.toStatus = :status and p.createdAt >= :from and p.createdAt < :to "
            + "and (:includeDemo = true or p.demo = false)")
    List<Object[]> transitionTimes(@Param("status") PickupStatus status, @Param("from") Instant from,
                                   @Param("to") Instant to, @Param("includeDemo") boolean includeDemo);

    @Modifying
    @Query("delete from PickupEvent e where e.pickup.id in (select p.id from Pickup p where p.demo = true)")
    void deleteDemoEvents();
}
