package com.ecotrace.repo;

import com.ecotrace.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);

    Page<AuditLog> findBySuccessFalseOrderByOccurredAtDescIdDesc(Pageable pageable);

    List<AuditLog> findByPickupReferenceOrderByOccurredAtDescIdDesc(String pickupReference);

    @Modifying
    @Query("delete from AuditLog a where a.demo = true")
    void deleteDemo();
}
