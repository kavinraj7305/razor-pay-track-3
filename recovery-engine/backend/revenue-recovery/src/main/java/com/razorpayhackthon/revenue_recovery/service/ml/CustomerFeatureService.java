package com.razorpayhackthon.revenue_recovery.service.ml;

import com.razorpayhackthon.revenue_recovery.entity.Payment;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.entity.Subscription;
import com.razorpayhackthon.revenue_recovery.enums.PaymentStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoverySource;
import com.razorpayhackthon.revenue_recovery.repository.PaymentRepository;
import com.razorpayhackthon.revenue_recovery.repository.SubscriptionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CustomerFeatureService {

	private final PaymentRepository paymentRepository;
	private final SubscriptionRepository subscriptionRepository;

	public CustomerFeatureService(
			PaymentRepository paymentRepository, SubscriptionRepository subscriptionRepository) {
		this.paymentRepository = paymentRepository;
		this.subscriptionRepository = subscriptionRepository;
	}

	public PredictPayload snapshot(RecoveryCase recoveryCase) {
		String customerId =
				recoveryCase.getCustomer() == null ? null : recoveryCase.getCustomer().getCustomerId();
		List<Payment> history = customerId == null
				? List.of()
				: paymentRepository.findByCustomer_CustomerId(customerId).stream()
						.filter(payment -> !isThisCasePayment(recoveryCase, payment))
						.sorted(Comparator.comparing(Payment::getCreatedAt))
						.toList();
		long success = history.stream().filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS).count();
		long fail = history.stream().filter(payment -> payment.getStatus() == PaymentStatus.FAILED).count();
		int n = history.size();
		double successRate = n == 0 ? 0 : (double) success / n;
		double failRate = n == 0 ? 0 : (double) fail / n;
		BigDecimal ltv = history.stream()
				.filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS)
				.map(Payment::getAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal aov = success == 0
				? BigDecimal.ZERO
				: ltv.divide(BigDecimal.valueOf(success), 2, RoundingMode.HALF_UP);
		double delay = avgDelayHours(history);
		int daysSince = daysSinceLast(history, recoveryCase.getCreatedAt());
		int subMonths = customerId == null ? 0 : subscriptionAgeMonths(customerId, recoveryCase.getCreatedAt());
		String method = history.isEmpty() || history.getLast().getPaymentType() == null
				? "card"
				: history.getLast().getPaymentType();
		long hours = Math.max(0, Duration.between(recoveryCase.getCreatedAt(), LocalDateTime.now()).toHours());
		return new PredictPayload(
				recoveryCase.getReason() == null ? "unknown" : recoveryCase.getReason(),
				recoveryCase.getSource().name(),
				recoveryCase.getPriority().name(),
				method,
				recoveryCase.getAmountAtRisk(),
				1,
				hours,
				0,
				0,
				round4(successRate),
				round4(failRate),
				round2(delay),
				subMonths,
				ltv.doubleValue(),
				aov.doubleValue(),
				daysSince,
				n);
	}

	private boolean isThisCasePayment(RecoveryCase recoveryCase, Payment payment) {
		return recoveryCase.getSource() == RecoverySource.PAYMENT
				&& recoveryCase.getSourceId().equals(payment.getPaymentId());
	}

	private double avgDelayHours(List<Payment> history) {
		if (history.size() < 2) {
			return 0;
		}
		double sum = 0;
		int gaps = 0;
		for (int i = 1; i < history.size(); i++) {
			sum += Duration.between(history.get(i - 1).getCreatedAt(), history.get(i).getCreatedAt()).toMinutes()
					/ 60.0;
			gaps++;
		}
		return gaps == 0 ? 0 : sum / gaps;
	}

	private int daysSinceLast(List<Payment> history, LocalDateTime fallback) {
		LocalDateTime last = history.isEmpty() ? fallback : history.getLast().getCreatedAt();
		return (int) Math.max(0, Duration.between(last, LocalDateTime.now()).toDays());
	}

	private int subscriptionAgeMonths(String customerId, LocalDateTime now) {
		return subscriptionRepository.findByCustomer_CustomerId(customerId).stream()
				.map(Subscription::getCreatedAt)
				.min(LocalDateTime::compareTo)
				.map(created -> (int) Math.max(0, Duration.between(created, now).toDays() / 30))
				.orElse(0);
	}

	private static double round4(double value) {
		return Math.round(value * 10000.0) / 10000.0;
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
