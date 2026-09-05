package com.razorpayhackthon.revenue_recovery.service.auth;

import com.razorpayhackthon.revenue_recovery.dto.auth.AssignRoleRequest;
import com.razorpayhackthon.revenue_recovery.dto.auth.CreateUserRequest;
import com.razorpayhackthon.revenue_recovery.dto.auth.DemoAccount;
import com.razorpayhackthon.revenue_recovery.dto.auth.LoginRequest;
import com.razorpayhackthon.revenue_recovery.dto.auth.SessionResponse;
import com.razorpayhackthon.revenue_recovery.dto.auth.UserRow;
import com.razorpayhackthon.revenue_recovery.entity.DeskUser;
import com.razorpayhackthon.revenue_recovery.enums.DeskRole;
import com.razorpayhackthon.revenue_recovery.repository.DeskUserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

	public static final List<DemoAccount> DEMO_ACCOUNTS = List.of(
			new DemoAccount(
					"ceo@recovery.local",
					"admin123",
					DeskRole.ADMIN,
					"Priya Shah · CEO",
					"Dashboard, recovery desk, and the queue"),
			new DemoAccount(
					"policy@recovery.local",
					"approve123",
					DeskRole.APPROVER,
					"Arjun Mehta · Human in the loop",
					"The approval queue only"));

	private final DeskUserRepository deskUserRepository;

	public AuthService(DeskUserRepository deskUserRepository) {
		this.deskUserRepository = deskUserRepository;
	}

	@Transactional
	public SessionResponse login(LoginRequest request) {
		if (request == null || blank(request.email()) || blank(request.password())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email and password required");
		}
		DeskUser user = deskUserRepository
				.findByEmailIgnoreCase(request.email().trim())
				.filter(DeskUser::getActive)
				.filter(found -> PasswordHasher.matches(request.password(), found.getPasswordHash()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
		user.setSessionToken(UUID.randomUUID().toString().replace("-", ""));
		return toSession(deskUserRepository.save(user));
	}

	@Transactional
	public void logout(DeskUser user) {
		user.setSessionToken(null);
		deskUserRepository.save(user);
	}

	public SessionResponse me(DeskUser user) {
		return toSession(user);
	}

	public List<UserRow> listUsers() {
		return deskUserRepository.findAllByOrderByCreatedAtAsc().stream().map(this::toRow).toList();
	}

	@Transactional
	public UserRow createUser(CreateUserRequest request) {
		if (request == null || blank(request.email()) || blank(request.displayName()) || blank(request.password())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email, name, and password required");
		}
		DeskRole role = assignable(request.role());
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		if (deskUserRepository.findByEmailIgnoreCase(email).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "email already exists");
		}
		DeskUser user = new DeskUser();
		user.setUserId("usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
		user.setEmail(email);
		user.setDisplayName(request.displayName().trim());
		user.setPasswordHash(PasswordHasher.hash(request.password()));
		user.setRole(role);
		user.setActive(Boolean.TRUE);
		return toRow(deskUserRepository.save(user));
	}

	@Transactional
	public UserRow assignRole(String userId, AssignRoleRequest request, DeskUser actor) {
		DeskRole role = assignable(request == null ? null : request.role());
		DeskUser user = deskUserRepository
				.findByUserId(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
		if (user.getUserId().equals(actor.getUserId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot change your own role");
		}
		if (user.getRole() == DeskRole.ADMIN) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot reassign the CEO admin");
		}
		user.setRole(role);
		return toRow(deskUserRepository.save(user));
	}

	public DeskUser requireToken(String token) {
		if (blank(token)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "sign in required");
		}
		return deskUserRepository
				.findBySessionToken(token)
				.filter(DeskUser::getActive)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session expired"));
	}

	private DeskRole assignable(DeskRole role) {
		if (role == DeskRole.APPROVER) {
			return role;
		}
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the only extra role is the human in the loop");
	}

	private SessionResponse toSession(DeskUser user) {
		return new SessionResponse(user.getSessionToken(), user.getUserId(), user.getEmail(), user.getDisplayName(), user.getRole());
	}

	private UserRow toRow(DeskUser user) {
		return new UserRow(
				user.getUserId(),
				user.getEmail(),
				user.getDisplayName(),
				user.getRole(),
				Boolean.TRUE.equals(user.getActive()),
				user.getCreatedAt());
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
