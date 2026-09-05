export function prettyWords(value: string) {
  return value.replaceAll("_", " ").replaceAll(".", " ").replace(/\s+/g, " ").trim();
}

export function statusLabel(status: string) {
  switch (status) {
    case "OPEN":
    case "ACTION_PLANNED":
    case "PLANNED":
      return "Ready";
    case "RECOVERED":
      return "Recovered";
    case "FAILED":
      return "Unpaid";
    case "EXPIRED":
      return "Expired";
    case "CANCELLED":
      return "Stopped";
    default:
      return prettyWords(status) || "Ready";
  }
}

export function actionLabel(value: string) {
  const map: Record<string, string> = {
    DELAYED_RETRY: "Retry later",
    RETRY_PAYMENT: "Retry the payment",
    SEND_PAYMENT_LINK: "Send a payment link",
    SEND_SMS: "Text the customer",
    SEND_EMAIL: "Email the customer",
    REQUEST_PROMISE_TO_PAY: "Ask for a promise to pay",
    SKIP_EXTRA_RETRY: "Skip extra retries",
    DO_NOT_RETRY: "Do not retry",
    NO_ACTION: "Leave it",
    ESCALATE: "Ask a person",
  };
  return map[value] ?? prettyWords(value);
}

export function stepResult(status: string) {
  switch (status) {
    case "EXECUTED":
      return "Done";
    case "FAILED":
      return "Didn’t come back";
    case "CANCELLED":
      return "Skipped";
    case "PLANNED":
      return "Planned";
    default:
      return prettyWords(status);
  }
}

export function chanceLabel(value: number | null | undefined, scoreStatus?: string | null) {
  if (scoreStatus === "LOW_DATA" || scoreStatus === "UNAVAILABLE" || value == null) {
    return null;
  }
  const pct = Math.round(value * 100);
  if (pct >= 60) {
    return `${pct}% likely to pay`;
  }
  if (pct >= 30) {
    return `${pct}% chance they pay`;
  }
  return `${pct}% unlikely to pay`;
}

export function stepNote(note: string) {
  return note.replace(/^Step\s+\d+:\s*/i, "").replace(/^T\+\S+\s*·\s*/i, "");
}

export function scheduleWhen(when: string | null | undefined, waitHours: number | null | undefined) {
  if (!when) {
    return null;
  }
  if (when === "T+0") {
    return "Runs at once (T+0)";
  }
  if (waitHours != null && waitHours > 0) {
    return `Scheduled ${when} after the failure (${waitHours} hours)`;
  }
  return `Scheduled ${when} after the failure`;
}

export function stepTitle(step: number, actionType: string) {
  if (actionType.includes("RETRY")) {
    if (step === 1) {
      return "First retry on the same card";
    }
    if (step === 2) {
      return "Second retry after a longer wait";
    }
    return "Last retry, then a text";
  }
  return actionLabel(actionType);
}

export function explainStep(input: {
  failureReason: string;
  actionType: string;
  step: number;
  status: string | null;
  actionNote: string | null;
  playbookNote: string;
  policyVerdict: string | null;
  policyReason?: string | null;
  recommendedAction?: string | null;
  scoreStatus?: string | null;
  mlScore?: number | null;
}): { what: string; why: string | null; result: string } {
  const skipped = input.status === "CANCELLED";
  const failed = input.status === "FAILED";
  const done = input.status === "EXECUTED";
  const what = stepNote(input.playbookNote) || actionLabel(input.actionType);
  const result = input.status ? stepResult(input.status) : "";
  return {
    what,
    why: skipped
      ? skipWhy(input)
      : failed
        ? failWhy(input)
        : done
          ? doneWhy(input)
          : waitWhy(input),
    result,
  };
}

function skipWhy(input: {
  failureReason: string;
  actionType: string;
  step: number;
  actionNote: string | null;
  policyVerdict: string | null;
  policyReason?: string | null;
  recommendedAction?: string | null;
  scoreStatus?: string | null;
  mlScore?: number | null;
}) {
  const note = (input.actionNote ?? "").toLowerCase();
  const agentSkip =
    input.recommendedAction === "SKIP_EXTRA_RETRY" || input.policyReason === "AGENT_SKIP_EXTRA_RETRY";
  const javaScoreUsed = input.scoreStatus === "SCORED" && input.mlScore != null;
  const p = input.mlScore != null ? `${Math.round(input.mlScore * 100)}%` : null;

  if (note.includes("ml skip") || (javaScoreUsed && input.policyVerdict === "SKIP_RETRY" && !agentSkip)) {
    return `Why: we already ran the first retry. P(recovery)${p ? ` was ${p}` : " was low"}, so extra waits at T+96h / T+5d were not run.`;
  }

  if (agentSkip || note.includes("agent_skip") || note.includes("after first retry") || note.includes("policy")) {
    return javaScoreUsed && p
      ? `Why: first retry already ran. Chance they pay was ${p}, so extra silent retries were skipped.`
      : "Why: first retry already ran. Extra silent retries were skipped so we do not loop the same card.";
  }

  if (input.failureReason.toLowerCase().includes("card_expired") || input.failureReason.toLowerCase().includes("expired")) {
    return "Why: this card is dead. A retry on it cannot succeed.";
  }
  return "Why: this step was not run so it does not repeat.";
}

function waitWhy(input: { actionType: string; step: number }) {
  if (input.actionType.includes("RETRY") && input.step === 1) {
    return "If we start, this waits once, then tries the same card.";
  }
  if (input.actionType.includes("RETRY")) {
    return "Only runs if an earlier retry still left the case unpaid.";
  }
  if (input.actionType === "SEND_PAYMENT_LINK") {
    return "This is the change of channel — one link, not another silent charge.";
  }
  return null;
}

function failWhy(input: { failureReason: string; actionNote: string | null }) {
  if (input.actionNote && !input.actionNote.toLowerCase().includes("policy") && !input.actionNote.toLowerCase().includes("ml ")) {
    return stepNote(input.actionNote);
  }
  if (input.failureReason.toLowerCase().includes("insufficient")) {
    return "This attempt failed. There still was not enough money on the same instrument.";
  }
  return "This attempt ran once and did not come back.";
}

function doneWhy(input: { actionType: string; actionNote: string | null; playbookNote: string }) {
  if (input.actionType === "SEND_PAYMENT_LINK") {
    return "Done — one link, so they can pay with another method when the money is back.";
  }
  if (input.actionType === "SEND_SMS") {
    return "Done — we texted once. We do not keep messaging.";
  }
  if (input.actionType === "SEND_EMAIL") {
    return "Done — we emailed once instead of charging again.";
  }
  if (input.actionNote) {
    return stepNote(input.actionNote);
  }
  return stepNote(input.playbookNote);
}

const REASON_BLURBS: Record<string, string> = {
  insufficient_funds: "The bank said there wasn’t enough money. We wait for payday and try once — not in a loop.",
  card_expired: "The saved card is dead. A retry on the same card will fail, so we send a new payment link.",
  payment_risk_check_failed:
    "Fraud or risk checks blocked the charge. Money stays put until the other person signs off.",
  "subscription.pending": "The mandate didn’t go through. We space out a few mandate retries.",
  "subscription.halted": "Retries already ran out on this subscription. Someone has to pick the next step.",
  "invoice.expired": "A B2B invoice timed out before it was paid. We chase receivables, not silent card retries.",
  "checkout.abandoned": "They left checkout. We send a pay link once — we don’t keep charging.",
  captured: "This one already came back. The case should close as recovered.",
  payment_cancelled: "The customer or merchant cancelled. We don’t retry a cancelled charge.",
  invalid_vpa: "The UPI address is wrong. We ask for a new VPA instead of retrying the same one.",
  gateway_technical: "The bank or gateway hiccuped. One short wait, then one retry.",
  bank_technical: "The bank had a technical miss. Wait once, then try again.",
};

export function reasonBlurb(reason: string) {
  const key = reason.toLowerCase();
  if (REASON_BLURBS[key]) {
    return REASON_BLURBS[key];
  }
  const match = Object.entries(REASON_BLURBS).find(([name]) => key.includes(name));
  if (match) {
    return match[1];
  }
  return "This payment failed and is sitting open. Start recovery to run the plan for it.";
}
