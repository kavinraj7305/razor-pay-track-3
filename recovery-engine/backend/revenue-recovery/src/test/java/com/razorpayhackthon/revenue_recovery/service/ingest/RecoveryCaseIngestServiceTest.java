package com.razorpayhackthon.revenue_recovery.service.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
import com.razorpayhackthon.revenue_recovery.repository.CustomerRepository;
import com.razorpayhackthon.revenue_recovery.repository.MerchantRepository;
import com.razorpayhackthon.revenue_recovery.repository.PaymentRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryCaseRepository;
import com.razorpayhackthon.revenue_recovery.repository.WebhookEventRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
		properties = {
			"razorpay.webhook-secret=whsec_test",
			"spring.kafka.listener.auto-startup=false"
		})
class RecoveryCaseIngestServiceTest {

	private static final String FAILED =
			"""
			{"id":"evt_case_fail_1","entity":"event","account_id":"acc_ingest_test","event":"payment.failed","contains":["payment"],"payload":{"payment":{"entity":{"id":"pay_ingest_fail_1","entity":"payment","amount":49900,"currency":"INR","status":"failed","order_id":"order_ingest_1","method":"card","email":"recovery.test@example.com","contact":"+919000000000","error_code":"BAD_REQUEST_ERROR","error_description":"Payment failed","error_reason":"payment_failed"}}},"created_at":1710000000}""";

	private static final String CAPTURED =
			"""
			{"id":"evt_case_ok_1","entity":"event","account_id":"acc_ingest_test","event":"payment.captured","contains":["payment"],"payload":{"payment":{"entity":{"id":"pay_ingest_fail_1","entity":"payment","amount":49900,"currency":"INR","status":"captured"}}},"created_at":1710000001}""";

	@Autowired
	private RecoveryCaseIngestService ingestService;

	@Autowired
	private RecoveryCaseRepository recoveryCaseRepository;

	@Autowired
	private WebhookEventRepository webhookEventRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private MerchantRepository merchantRepository;

	@BeforeEach
	void clean() {
		recoveryCaseRepository.deleteAll(
				recoveryCaseRepository.findByMerchant_MerchantId("acc_ingest_test"));
		paymentRepository.deleteAll(paymentRepository.findByMerchant_MerchantId("acc_ingest_test"));
		customerRepository.deleteAll(customerRepository.findByMerchant_MerchantId("acc_ingest_test"));
		merchantRepository.findByMerchantId("acc_ingest_test").ifPresent(merchantRepository::delete);
		webhookEventRepository.findByEventId("evt_case_fail_1").ifPresent(webhookEventRepository::delete);
		webhookEventRepository.findByEventId("evt_case_ok_1").ifPresent(webhookEventRepository::delete);
	}

	@Test
	void paymentFailedCreatesRecoveryCaseWithAmountAtRiskInRupees() {
		ingestService.consume(FAILED);

		List<RecoveryCase> cases =
				recoveryCaseRepository.findBySourceAndSourceId(RecoverySource.PAYMENT, "pay_ingest_fail_1");
		assertThat(cases).hasSize(1);
		RecoveryCase recoveryCase = cases.getFirst();
		assertThat(recoveryCase.getAmountAtRisk()).isEqualByComparingTo(new BigDecimal("499.00"));
		assertThat(recoveryCase.getCurrency()).isEqualTo("INR");
		assertThat(recoveryCase.getStatus()).isEqualTo(RecoveryCaseStatus.ACTION_PLANNED);
		assertThat(recoveryCase.getReason()).isEqualTo("payment_failed");
		assertThat(merchantRepository.findByMerchantId("acc_ingest_test")).isPresent();
		assertThat(webhookEventRepository.findByEventId("evt_case_fail_1"))
				.isPresent()
				.get()
				.extracting("processed")
				.isEqualTo(true);

		ingestService.consume(FAILED);
		assertThat(
						recoveryCaseRepository.findBySourceAndSourceId(
								RecoverySource.PAYMENT, "pay_ingest_fail_1"))
				.hasSize(1);
	}

	@Test
	void paymentCapturedClosesOpenRecoveryCase() {
		ingestService.consume(FAILED);
		ingestService.consume(CAPTURED);

		RecoveryCase recoveryCase =
				recoveryCaseRepository
						.findBySourceAndSourceId(RecoverySource.PAYMENT, "pay_ingest_fail_1")
						.getFirst();
		assertThat(recoveryCase.getStatus()).isEqualTo(RecoveryCaseStatus.RECOVERED);
		assertThat(recoveryCase.getClosedAt()).isNotNull();
	}
}
