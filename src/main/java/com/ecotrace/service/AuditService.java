package com.ecotrace.service;

import com.ecotrace.domain.AuditLog;
import com.ecotrace.dto.Dtos.AuditView;
import com.ecotrace.dto.Dtos.PageView;
import com.ecotrace.repo.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository audits;

    public AuditService(AuditLogRepository audits) {
        this.audits = audits;
    }

    @Transactional(readOnly = true)
    public PageView<AuditView> list(boolean failedOnly, int page, int size) {
        var pageable = Paging.of(page, size, Sort.unsorted());
        Page<AuditLog> result = failedOnly
                ? audits.findBySuccessFalseOrderByOccurredAtDescIdDesc(pageable)
                : audits.findAllByOrderByOccurredAtDescIdDesc(pageable);
        return Paging.view(result, a -> new AuditView(a.getId(), a.getPickupReference(), a.getActorName(),
                a.getActorRole(), a.isSuccess(), a.getMessage(), a.getOccurredAt()));
    }
}
