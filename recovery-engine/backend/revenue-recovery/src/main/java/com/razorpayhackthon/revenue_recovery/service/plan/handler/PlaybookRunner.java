package com.razorpayhackthon.revenue_recovery.service.plan.handler;

import com.razorpayhackthon.revenue_recovery.dto.PlaybookStepPreview;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryAction;
import com.razorpayhackthon.revenue_recovery.entity.RecoveryCase;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionStatus;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryActionType;
import com.razorpayhackthon.revenue_recovery.enums.RecoveryCaseStatus;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryActionRepository;
import com.razorpayhackthon.revenue_recovery.repository.RecoveryCaseRepository;
import com.razorpayhackthon.revenue_recovery.service.plan.PlaybookClock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlaybookRunner {

	private final RecoveryActionRepository recoveryActionRepository;
	private final RecoveryCaseRepository recoveryCaseRepository;

	public PlaybookRunner(
			RecoveryActionRepository recoveryActionRepository, RecoveryCaseRepository recoveryCaseRepository) {
		this.recoveryActionRepository = recoveryActionRepository;
		this.recoveryCaseRepository = recoveryCaseRepository;
	}

	public int executeNext(
			RecoveryCase recoveryCase, List<? extends PlaybookStep> steps, String playbookName) {
		int lastStep = steps.stream().mapToInt(PlaybookStep::stepNumber).max().orElse(0);
		int next = nextStepNumber(recoveryCase);
		if (next > lastStep) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, playbookName + " steps are finished");
		}
		PlaybookStep step = steps.stream()
				.filter(candidate -> candidate.stepNumber() == next)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No " + playbookName + " step " + next));
		RecoveryAction action = openAction(recoveryCase, step);
		recoveryCase.setStatus(RecoveryCaseStatus.RECOVERING);
		step.execute(recoveryCase, action);
		if (action.getStatus() == RecoveryActionStatus.EXECUTED
				&& action.getActionType() == RecoveryActionType.RETRY_PAYMENT) {
			recoveryCase.setStatus(RecoveryCaseStatus.RECOVERED);
			recoveryCase.setClosedAt(LocalDateTime.now());
		}
		recoveryActionRepository.save(action);
		recoveryCaseRepository.save(recoveryCase);
		return next;
	}

	/** Mark upcoming silent retries as done-cancelled so the playbook can advance to a pay-link / SMS. */
	public int skipUpcomingRetries(RecoveryCase recoveryCase, List<PlaybookStepPreview> playbook, String why) {
		int last = playbook.stream().mapToInt(PlaybookStepPreview::step).max().orElse(0);
		int skipped = 0;
		while (true) {
			int next = nextStepNumber(recoveryCase);
			if (next > last) {
				return skipped;
			}
			int stepNumber = next;
			PlaybookStepPreview preview = playbook.stream()
					.filter(candidate -> candidate.step() == stepNumber)
					.findFirst()
					.orElse(null);
			if (preview == null || !"RETRY_PAYMENT".equals(preview.actionType())) {
				return skipped;
			}
			skipRetryStep(recoveryCase, preview, why);
			skipped++;
		}
	}

	public int nextStepNumber(RecoveryCase recoveryCase) {
		return recoveryActionRepository.findByRecoveryCase_CaseId(recoveryCase.getCaseId()).stream()
						.filter(this::countsAsDone)
						.mapToInt(action -> action.getAttemptNumber() == null ? 0 : action.getAttemptNumber())
						.max()
						.orElse(0)
				+ 1;
	}

	private boolean countsAsDone(RecoveryAction action) {
		if (action.getStatus() == RecoveryActionStatus.EXECUTED
				|| action.getStatus() == RecoveryActionStatus.FAILED) {
			return true;
		}
		return action.getStatus() == RecoveryActionStatus.CANCELLED && action.getExecutedAt() != null;
	}

	private RecoveryAction openAction(RecoveryCase recoveryCase, PlaybookStep step) {
		List<RecoveryAction> existing =
				recoveryActionRepository.findByRecoveryCase_CaseId(recoveryCase.getCaseId());
		for (RecoveryAction action : existing) {
			if (action.getAttemptNumber() != null
					&& action.getAttemptNumber() == step.stepNumber()
					&& action.getStatus() == RecoveryActionStatus.PLANNED) {
				return action;
			}
		}
		RecoveryAction action = new RecoveryAction();
		action.setActionId("act_" + UUID.randomUUID().toString().replace("-", ""));
		action.setRecoveryCase(recoveryCase);
		action.setActionType(step.actionType());
		action.setStatus(RecoveryActionStatus.PLANNED);
		action.setAttemptNumber(step.stepNumber());
		stampSchedule(recoveryCase, action, step.stepNumber(), step.planNote());
		return recoveryActionRepository.save(action);
	}

	private void skipRetryStep(RecoveryCase recoveryCase, PlaybookStepPreview preview, String why) {
		RecoveryAction action = recoveryActionRepository.findByRecoveryCase_CaseId(recoveryCase.getCaseId()).stream()
				.filter(existing -> existing.getAttemptNumber() != null
						&& existing.getAttemptNumber() == preview.step()
						&& existing.getStatus() == RecoveryActionStatus.PLANNED)
				.findFirst()
				.orElseGet(() -> {
					RecoveryAction created = new RecoveryAction();
					created.setActionId("act_" + UUID.randomUUID().toString().replace("-", ""));
					created.setRecoveryCase(recoveryCase);
					created.setActionType(RecoveryActionType.RETRY_PAYMENT);
					created.setAttemptNumber(preview.step());
					created.setReason(preview.note());
					return created;
				});
		action.setStatus(RecoveryActionStatus.CANCELLED);
		action.setExecutedAt(LocalDateTime.now());
		PlaybookClock.Window window = PlaybookClock.of(recoveryCase.getReason(), preview.step());
		action.setWaitHours(window.hours());
		action.setScheduleLabel(window.label());
		action.setReason(window.label() + " · " + why);
		recoveryActionRepository.save(action);
	}

	private void stampSchedule(RecoveryCase recoveryCase, RecoveryAction action, int step, String note) {
		PlaybookClock.Window window = PlaybookClock.of(recoveryCase.getReason(), step);
		action.setWaitHours(window.hours());
		action.setScheduleLabel(window.label());
		action.setReason(window.label() + " · " + note);
	}
}
