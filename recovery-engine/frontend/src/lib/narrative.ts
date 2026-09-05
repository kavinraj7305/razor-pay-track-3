import type { CaseDetail, PlannedAction, PlaybookStep } from "./types";

export type StepStory = {
  waitLabel: string;
  waitMs: number;
  clockLabel: string;
  what: string;
  why: string;
};

/** One wait per step — compressed for the pitch (not a continuous retry loop). */
export function storyFor(reason: string, step: PlaybookStep, probability: number | null): StepStory {
  const key = (reason || "").toLowerCase();
  const p = probability == null ? "unknown" : `${Math.round(probability * 100)}%`;

  if (key.includes("insufficient_funds")) {
    if (step.step === 1) {
      return {
        waitLabel: "One silent retry after payday window",
        waitMs: 1600,
        clockLabel: "T+48h · once",
        what: "Single delayed retry on the same instrument. Not a continuous loop.",
        why: `A shortfall is often temporary. The playbook waits for payday, then tries once. P(recovery)=${p}.`,
      };
    }
    if (step.step === 2) {
      return {
        waitLabel: "One second retry after a longer gap",
        waitMs: 1800,
        clockLabel: "T+96h · once",
        what: "Second attempt only — still one shot at this window, then we move on.",
        why: "First attempt failed. Wait longer once, retry once, then escalate if it fails again.",
      };
    }
    if (step.step === 3) {
      return {
        waitLabel: "Last auto-retry + SMS (once)",
        waitMs: 1500,
        clockLabel: "T+5d · once",
        what: "Final automatic retry, then notify the customer.",
        why: "Three spaced attempts max. No infinite retrying.",
      };
    }
    return {
      waitLabel: "Stop retries — send payment link once",
      waitMs: 1200,
      clockLabel: "After retries",
      what: "Auto-retry ends. Send one payment link for another method.",
      why: "Same instrument failed the spaced attempts. Change channel instead of hammering.",
    };
  }

  if (key.includes("payment_risk_check_failed") || key.includes("risk")) {
    const clocks = ["T+0", "T+15m", "T+24h", "Stop"];
    const waits = [900, 1100, 1400, 900];
    return {
      waitLabel: step.note || "Risk playbook step",
      waitMs: waits[Math.min(step.step - 1, 3)],
      clockLabel: `${clocks[Math.min(step.step - 1, 3)]} · once`,
      what: step.note,
      why: "Risk cases never auto-retry charges. Each step runs once, then stops.",
    };
  }

  if (key.includes("card_expired") || key.includes("invalid_vpa") || key.includes("checkout.abandoned")) {
    return {
      waitLabel: step.step === 1 ? "Send link (no card retry)" : "One nudge only",
      waitMs: step.step === 1 ? 1100 : 1300,
      clockLabel: step.step === 1 ? "T+0 · once" : `Nudge ${step.step - 1} · once`,
      what: step.note,
      why: "Dead instrument / abandon — remind once per step, never silent-retry forever.",
    };
  }

  if (key.includes("subscription.pending")) {
    return {
      waitLabel: "One mandate retry at this window",
      waitMs: 1500,
      clockLabel: step.step === 1 ? "T+24h · once" : step.step === 2 ? "T+48h · once" : "T+72h · once",
      what: step.note,
      why: "Sequencer: one debit attempt per window, then warn — not a tight loop.",
    };
  }

  if (key.includes("subscription.halted") || key.includes("invoice.expired")) {
    return {
      waitLabel: "One playbook action",
      waitMs: 1300,
      clockLabel: `Step ${step.step} · once`,
      what: step.note,
      why: "Each step is a single action, then the next scheduled step — not continuous retry.",
    };
  }

  return {
    waitLabel: "One playbook step",
    waitMs: 1200,
    clockLabel: `Step ${step.step} · once`,
    what: step.note || step.actionType.split("_").join(" "),
    why: `Reason=${reason}. P(recovery)=${p}. One execution per step.`,
  };
}

export function outcomeFor(action: PlannedAction | undefined, reason: string): {
  label: string;
  tone: "fail" | "ok" | "stop";
  detail: string;
} {
  if (!action) {
    return {
      label: "No progress",
      tone: "stop",
      detail: "This step did not create a new result — stopping so we do not retry forever.",
    };
  }
  const note = action.note ?? "";
  if (action.status === "FAILED") {
    return {
      label: "This attempt failed",
      tone: "fail",
      detail:
        note ||
        `One retry failed for ${reason}. That attempt is finished — next step is a later window, not an immediate loop.`,
    };
  }
  if (action.status === "CANCELLED") {
    return {
      label: "Blocked / skipped",
      tone: "stop",
      detail: note || "Policy or ML skip blocked this action. We do not keep retrying it.",
    };
  }
  if (action.actionType === "SEND_PAYMENT_LINK") {
    return {
      label: "Link sent once",
      tone: "ok",
      detail: note || "Payment link issued (DEV). Retries stop here.",
    };
  }
  if (action.actionType === "REQUEST_PROMISE_TO_PAY") {
    return {
      label: "PTP chase once",
      tone: "ok",
      detail: note || "Promise-to-pay chase recorded.",
    };
  }
  return {
    label: "Step done once",
    tone: "ok",
    detail: note || `${action.actionType} → ${action.status}`,
  };
}

export function latestActionForStep(detail: CaseDetail, step: number): PlannedAction | undefined {
  return [...(detail.actions ?? [])]
    .reverse()
    .find((action) => action.attemptNumber === step && action.status !== "PLANNED");
}

export type LogLine = {
  id: string;
  clock: string;
  title: string;
  body: string;
  tone: "info" | "wait" | "fail" | "ok" | "stop";
};
