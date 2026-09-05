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

export function chanceLabel(value: number | null | undefined, _scoreStatus?: string | null) {
  if (value == null || Number.isNaN(value)) {
    return null;
  }
  const pct = Math.round(value * 100);
  if (pct >= 60) {
    return `P(recovery) ${pct}% — likely to pay`;
  }
  if (pct >= 30) {
    return `P(recovery) ${pct}%`;
  }
  return `P(recovery) ${pct}% — too low to keep retrying`;
}

export function pickRecoveryChance(input: {
  proposalScore?: number | null;
  caseScore?: number | null;
  actionNotes?: Array<string | null | undefined> | null;
  audit?: Array<{ eventType?: string; details?: Record<string, unknown> | null }> | null;
}): number | null {
  const candidates = [input.proposalScore, input.caseScore];
  for (const line of [...(input.audit ?? [])].reverse()) {
    const details = line.details;
    if (!details) {
      continue;
    }
    candidates.push(
      asChance(details.recoveryProbability),
      asChance(details.mlScore),
      chanceFromNote(typeof details.reasoning === "string" ? details.reasoning : null),
      chanceFromNote(typeof details.reason === "string" ? details.reason : null),
    );
  }
  for (const note of input.actionNotes ?? []) {
    candidates.push(chanceFromNote(note));
  }
  return candidates.find((value): value is number => value != null) ?? null;
}

function chanceFromNote(note: string | null | undefined): number | null {
  if (!note) {
    return null;
  }
  const equals = note.match(/P(?:\(recovery\))?\s*=\s*([0-9.]+)/i);
  if (equals) {
    return asChance(equals[1]);
  }
  const percent = note.match(/P\(recovery\)[^\d]*(\d+)\s*%/i);
  return percent ? asChance(Number(percent[1]) / 100) : null;
}

function asChance(value: unknown): number | null {
  if (value == null || value === "") {
    return null;
  }
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
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
  const extraRetry = input.actionType.includes("RETRY") && input.step > 1;
  const skippedExtras =
    extraRetry ||
    note.includes("skip extra") ||
    note.includes("low p") ||
    note.includes("after first retry") ||
    input.recommendedAction === "SKIP_EXTRA_RETRY" ||
    input.policyVerdict === "SKIP_RETRY" ||
    input.policyReason === "AGENT_SKIP_EXTRA_RETRY";
  const p =
    input.mlScore != null && Number.isFinite(input.mlScore) ? `${Math.round(input.mlScore * 100)}%` : null;

  if (input.failureReason.toLowerCase().includes("card_expired") || input.failureReason.toLowerCase().includes("expired")) {
    return "Why: this card is dead. A retry on it cannot succeed.";
  }

  if (skippedExtras) {
    return p
      ? `Why: first retry already ran. P(recovery) was ${p} — too low to keep charging the same card, so this extra silent retry was skipped.`
      : "Why: first retry already ran. P(recovery) was too low to keep charging the same card, so this extra silent retry was skipped.";
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

export function howThisRuns(reason: string) {
  const key = reason.toLowerCase();
  if (key.includes("insufficient")) {
    return "Start always runs the first payday retry — that try is cheap. Extra silent retries are skipped only when P(recovery) is below 12%. High chance they pay means we keep chasing.";
  }
  if (key.includes("risk") || key.includes("cancelled")) {
    return "Start does not charge. This sits for the other person. You cannot skip that hold.";
  }
  if (key.includes("card_expired") || key.includes("invalid_vpa") || key.includes("checkout")) {
    return "Start does not retry the dead or abandoned method. It sends one payment link, then at most a couple of reminders.";
  }
  if (key.includes("invoice")) {
    return "Start chases a promise to pay. It does not silent-retry a card.";
  }
  if (key.includes("subscription.pending")) {
    return "Start retries the mandate once, then may skip extra retries if chance they pay is low.";
  }
  if (key.includes("subscription.halted")) {
    return "Start sends a link to update the mandate. Retries already ran out.";
  }
  return "Start runs the reason plan one step at a time. The first required step always runs. Extra silent retries can be skipped after that.";
}

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
