package com.ecotrace.repo;

import com.ecotrace.domain.HandoverCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HandoverCodeRepository extends JpaRepository<HandoverCode, Long> {

    /** Codes that have not been used or revoked, newest first. Read-only. */
    @Query("select c from HandoverCode c where c.pickup.id = :pickupId and c.usedAt is null and c.revokedAt is null "
            + "order by c.createdAt desc, c.id desc")
    List<HandoverCode> findOpen(@Param("pickupId") Long pickupId);

    /**
     * The same list, row-locked for the duration of the transaction. Used when a code is checked or issued,
     * so two simultaneous attempts cannot both pass the attempt limit or both use the same code.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from HandoverCode c where c.pickup.id = :pickupId and c.usedAt is null and c.revokedAt is null "
            + "order by c.createdAt desc, c.id desc")
    List<HandoverCode> findOpenForUpdate(@Param("pickupId") Long pickupId);

    /** Demo reset only: removes codes that belong to demonstration pickups. */
    @Modifying
    @Query("delete from HandoverCode c where c.pickup.id in (select p.id from Pickup p where p.demo = true)")
    void deleteForDemoPickups();
}
