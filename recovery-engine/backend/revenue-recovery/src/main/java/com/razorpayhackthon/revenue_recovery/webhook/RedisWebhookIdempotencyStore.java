package com.razorpayhackthon.revenue_recovery.webhook;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisWebhookIdempotencyStore implements WebhookIdempotencyStore {

	static final String KEY_PREFIX = "idempotency:webhook:";

	private final StringRedisTemplate redis;
	private final Duration ttl;

	public RedisWebhookIdempotencyStore(
			StringRedisTemplate redis,
			@Value("${recovery.webhook.idempotency-ttl:24h}") Duration ttl) {
		this.redis = redis;
		this.ttl = ttl;
	}

	@Override
	public boolean tryClaim(String eventId) {
		Boolean first = redis.opsForValue().setIfAbsent(key(eventId), "1", ttl);
		return Boolean.TRUE.equals(first);
	}

	@Override
	public void release(String eventId) {
		redis.delete(key(eventId));
	}

	public static String key(String eventId) {
		return KEY_PREFIX + eventId;
	}
}
