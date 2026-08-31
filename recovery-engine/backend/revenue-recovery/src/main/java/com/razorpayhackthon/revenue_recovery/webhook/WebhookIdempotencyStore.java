package com.razorpayhackthon.revenue_recovery.webhook;

public interface WebhookIdempotencyStore {

	/**
	 * Redis SETNX {@code idempotency:webhook:{eventId}}. Returns {@code true} if this is the first
	 * sighting of the event.
	 */
	boolean tryClaim(String eventId);

	void release(String eventId);
}
