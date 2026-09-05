package com.razorpayhackthon.revenue_recovery.service.auth;

import com.razorpayhackthon.revenue_recovery.dto.auth.DemoAccount;
import com.razorpayhackthon.revenue_recovery.entity.DeskUser;
import com.razorpayhackthon.revenue_recovery.enums.DeskRole;
import com.razorpayhackthon.revenue_recovery.repository.DeskUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DeskUserSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DeskUserSeeder.class);

	private final DeskUserRepository deskUserRepository;

	public DeskUserSeeder(DeskUserRepository deskUserRepository) {
		this.deskUserRepository = deskUserRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		int index = 1;
		for (DemoAccount demo : AuthService.DEMO_ACCOUNTS) {
			if (deskUserRepository.findByEmailIgnoreCase(demo.email()).isPresent()) {
				index++;
				continue;
			}
			DeskUser user = new DeskUser();
			user.setUserId("usr_demo_" + index++);
			user.setEmail(demo.email());
			user.setDisplayName(demo.displayName());
			user.setPasswordHash(PasswordHasher.hash(demo.password()));
			user.setRole(demo.role());
			user.setActive(Boolean.TRUE);
			deskUserRepository.save(user);
		}
		deskUserRepository.findAllByOrderByCreatedAtAsc().stream()
				.filter(user -> user.getRole() == DeskRole.OPERATOR)
				.forEach(user -> {
					user.setActive(Boolean.FALSE);
					user.setSessionToken(null);
					deskUserRepository.save(user);
				});
		log.info("Desk has two roles — CEO and human in the loop");
	}
}
