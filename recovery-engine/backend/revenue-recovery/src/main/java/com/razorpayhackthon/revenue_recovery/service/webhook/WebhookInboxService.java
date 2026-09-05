package com.razorpayhackthon.revenue_recovery.service.webhook;

import com.razorpayhackthon.revenue_recovery.dto.WebhookInboxItem;
import com.razorpayhackthon.revenue_recovery.dto.WebhookInboxSnapshot;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.entity.WebhookEvent;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
import com.razorpayhackthon.revenue_recovery.enums.WebhookIntake;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryCaseRepository;
import com.razorpayhackthon.revenue_recovery.repository.WebhookEventRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WebhookInboxService {

	static final Set<String> LOCAL_DEMO_ACCOUNTS =
			Set.of("acc_test_recovery", "acc_syn_training", "acc_ingest_test", "acc_test");

	private final WebhookEventRepository webhookEventRepository;
	private final RecoveryCaseRepository recoveryCaseRepository;

	public WebhookInboxService(
			WebhookEventRepository webhookEventRepository, RecoveryCaseRepository recoveryCaseRepository) {
		this.webhookEventRepository = webhookEventRepository;
		this.recoveryCaseRepository = recoveryCaseRepository;
	}

	public WebhookInboxSnapshot snapshot() {
		List<WebhookInboxItem> events =
				webhookEventRepository.findTop20ByOrderByReceivedAtDesc().stream().map(this::toItem).toList();
		int signed = (int) events.stream().filter(WebhookInboxItem::signatureVerified).count();
		int razorpay = (int) events.stream().filter(item -> "RAZORPAY".equals(item.origin())).count();
		int simulate = (int) events.stream().filter(item -> "DESK_SIMULATE".equals(item.origin())).count();
		return new WebhookInboxSnapshot(signed, razorpay, simulate, events);
	}

	private WebhookInboxItem toItem(WebhookEvent event) {
		String accountId = text(event.getPayload(), "account_id");
		String sourceId = sourceIdOf(event);
		RecoveryCase linked = findCase(event.getEventType(), sourceId);
		return new WebhookInboxItem(
				event.getEventId(),
				event.getEventType(),
				accountId,
				event.getIntake(),
				event.isSignatureVerified(),
				originOf(event, accountId),
				event.isProcessed(),
				event.getReceivedAt(),
				sourceId,
				linked == null ? null : linked.getCaseId(),
				linked == null ? null : linked.getReason());
	}

	static String originOf(WebhookEvent event, String accountId) {
		if (!event.isSignatureVerified() || !WebhookIntake.HMAC_SIGNED.name().equals(event.getIntake())) {
			return "DESK_SIMULATE";
		}
		if (accountId != null && LOCAL_DEMO_ACCOUNTS.contains(accountId)) {
			return "LOCAL_SCRIPT";
		}
		return "RAZORPAY";
	}

	private RecoveryCase findCase(String eventType, String sourceId) {
		if (sourceId == null || sourceId.isBlank()) {
			return null;
		}
		RecoverySource source = sourceFor(eventType);
		if (source == null) {
			return null;
		}
		List<RecoveryCase> cases = recoveryCaseRepository.findBySourceAndSourceId(source, sourceId);
		return cases.isEmpty() ? null : cases.getFirst();
	}

	private static RecoverySource sourceFor(String eventType) {
		if (eventType == null) {
			return null;
		}
		if (eventType.startsWith("payment.")) {
			return RecoverySource.PAYMENT;
		}
		if (eventType.startsWith("subscription.")) {
			return RecoverySource.SUBSCRIPTION;
		}
		if (eventType.startsWith("invoice.")) {
			return RecoverySource.INVOICE;
		}
		if (eventType.startsWith("checkout.") || eventType.startsWith("order.")) {
			return RecoverySource.CHECKOUT_SESSION;
		}
		return null;
	}

	private static String sourceIdOf(WebhookEvent event) {
		Object payload = nested(event.getPayload(), "payload");
		if (!(payload instanceof Map<?, ?> envelope)) {
			return null;
		}
		for (String name : List.of("payment", "subscription", "invoice", "checkout", "order")) {
			String id = entityId(envelope, name);
			if (id != null) {
				return id;
			}
		}
		return null;
	}

	private static String entityId(Map<?, ?> payload, String name) {
		Object wrapper = payload.get(name);
		if (!(wrapper instanceof Map<?, ?> object)) {
			return null;
		}
		Object entity = object.get("entity");
		if (!(entity instanceof Map<?, ?> body)) {
			return null;
		}
		Object id = body.get("id");
		return id == null ? null : String.valueOf(id);
	}

	private static Object nested(Map<String, Object> root, String key) {
		return root == null ? null : root.get(key);
	}

	private static String text(Map<String, Object> root, String key) {
		if (root == null || root.get(key) == null) {
			return null;
		}
		String value = String.valueOf(root.get(key));
		return value.isBlank() ? null : value;
	}
}
