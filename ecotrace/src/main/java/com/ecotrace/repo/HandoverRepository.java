package com.ecotrace.repo;

import com.ecotrace.domain.Handover;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface HandoverRepository extends JpaRepository<Handover, Long> {

    Optional<Handover> findByPickupId(Long pickupId);

    boolean existsByPickupId(Long pickupId);

    boolean existsByHandoverReference(String handoverReference);

    Page<Handover> findAllByOrderByVerifiedAtDescIdDesc(Pageable pageable);

    /** Demo reset only. Photos must be removed first (see HandoverPhotoRepository). */
    @Modifying
    @Query("delete from Handover h where h.pickup.id in (select p.id from Pickup p where p.demo = true)")
    void deleteForDemoPickups();
}
