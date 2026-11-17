package com.ecotrace.repo;

import com.ecotrace.domain.HandoverPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface HandoverPhotoRepository extends JpaRepository<HandoverPhoto, Long> {

    Optional<HandoverPhoto> findByHandoverId(Long handoverId);

    boolean existsByHandoverId(Long handoverId);

    /** Demo reset only: photos of demonstration handovers. Runs before the handovers are deleted. */
    @Modifying
    @Query("delete from HandoverPhoto ph where ph.handover.id in (select h.id from Handover h "
            + "where h.pickup.id in (select p.id from Pickup p where p.demo = true))")
    void deleteForDemoPickups();
}
