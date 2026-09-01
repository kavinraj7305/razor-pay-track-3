package com.razorpayhackthon.revenue_recovery.service.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Free local SMS: logs only. Swap this class later for Twilio/MSG91.
 */
@Service
public class DevSmsService {

	private static final Logger log = LoggerFactory.getLogger(DevSmsService.class);

	public void send(String to, String body) {
		log.info("DEV SMS to={} body={}", to == null || to.isBlank() ? "unknown" : to, body);
	}
}
