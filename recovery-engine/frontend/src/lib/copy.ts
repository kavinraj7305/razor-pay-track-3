export function prettyWords(value: string) {
  return value.replaceAll("_", " ").replaceAll(".", " ").replace(/\s+/g, " ").trim();
}

export function statusLabel(status: string) {
  switch (status) {
    case "OPEN":
      return "Open";
    case "RECOVERED":
      return "Recovered";
    case "FAILED":
      return "Failed";
    case "EXPIRED":
      return "Expired";
    case "CANCELLED":
      return "Cancelled";
    default:
      return prettyWords(status) || "Open";
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
  if (scoreStatus === "LOW_DATA") {
    return "Not enough history — usual plan";
  }
  if (scoreStatus === "UNAVAILABLE" || value == null) {
    return "Usual plan";
  }
  const pct = Math.round(value * 100);
  if (pct >= 60) {
    return `${pct}% — likely to pay`;
  }
  if (pct >= 30) {
    return `${pct}% — uncertain`;
  }
  return `${pct}% — unlikely to pay`;
}

export function policyBanner(reason: string, verdict: string) {
  if (reason === "HUMAN_OVERRIDE") {
    return "Signed off. You can continue recovery.";
  }
  if (verdict === "BLOCK") {
    return "Held for the other person. You cannot start until they let it through.";
  }
  if (verdict === "SKIP_RETRY") {
    return "Extra retries were skipped. The rest of the plan can still run.";
  }
  return "Cleared to continue.";
}

export function auditTitle(eventType: string) {
  if (eventType === "AGENT_PROPOSE") {
    return "Suggested a next step";
  }
  if (eventType === "POLICY_BLOCK") {
    return "Held for review";
  }
  if (eventType === "POLICY_SKIP_RETRY") {
    return "Skipped an extra retry";
  }
  if (eventType === "POLICY_ALLOW" || eventType === "POLICY_APPROVED") {
    return "Cleared to continue";
  }
  if (eventType === "POLICY_REJECTED") {
    return "Kept on hold";
  }
  if (eventType.includes("ACTION") && eventType.includes("FAIL")) {
    return "This attempt failed";
  }
  if (eventType.includes("ACTION") && (eventType.includes("EXECUTE") || eventType.includes("DONE"))) {
    return "Ran a recovery step";
  }
  if (eventType.includes("PLANNED") || eventType.includes("PLAN")) {
    return "Planned the next step";
  }
  if (eventType.includes("SCORE")) {
    return "Scored this customer";
  }
  if (eventType.includes("CASE") && eventType.includes("OPEN")) {
    return "Opened this case";
  }
  if (eventType.includes("RECOVER")) {
    return "Marked recovered";
  }
  return prettyWords(eventType);
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
