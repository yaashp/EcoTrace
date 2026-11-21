package com.ecotrace.repo;

import com.ecotrace.domain.AppUser;
import com.ecotrace.domain.Partner;
import com.ecotrace.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    List<AppUser> findByRoleAndActiveTrue(Role role);

    List<AppUser> findByPartnerAndRoleAndActiveTrue(Partner partner, Role role);

    @Query("select u from AppUser u where u.role in :roles and (lower(u.name) like :q or lower(u.email) like :q)")
    Page<AppUser> search(@Param("roles") Collection<Role> roles, @Param("q") String q, Pageable pageable);
}
