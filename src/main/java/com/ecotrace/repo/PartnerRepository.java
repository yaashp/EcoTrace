package com.ecotrace.repo;

import com.ecotrace.domain.Partner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

    List<Partner> findByActiveTrueOrderByNameAsc();

    List<Partner> findAllByOrderByNameAsc();
}
