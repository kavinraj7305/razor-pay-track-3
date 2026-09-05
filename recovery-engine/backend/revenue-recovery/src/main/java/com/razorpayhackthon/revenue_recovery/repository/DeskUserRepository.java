package com.razorpayhackthon.revenue_recovery.repository;

import com.razorpayhackthon.revenue_recovery.entity.DeskUser;
import com.razorpayhackthon.revenue_recovery.enums.DeskRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeskUserRepository extends JpaRepository<DeskUser, Long> {

	Optional<DeskUser> findByEmailIgnoreCase(String email);

	Optional<DeskUser> findByUserId(String userId);

	Optional<DeskUser> findBySessionToken(String sessionToken);

	List<DeskUser> findAllByOrderByCreatedAtAsc();

	long countByRoleAndActiveTrue(DeskRole role);
}
