import { reasonBlurb } from "@/lib/copy";

export type CatalogStep = {
  title: string;
  when: string;
  what: string;
};

export type CatalogPlaybook = {
  reason: string;
  why: string;
  rule: string;
  steps: CatalogStep[];
};

const FOLDERS: Record<string, { rule: string; steps: CatalogStep[] }> = {
  captured: {
    rule: "Already paid. Close it. Do not chase.",
    steps: [
      { title: "Close as recovered", when: "Now", what: "The payment already came back. No retry, no link, no text." },
    ],
  },
  insufficient_funds: {
    rule: "Money is late, not dead. First payday try always runs. Extra silent tries only if they still look likely to pay.",
    steps: [
      { title: "First retry on the same card", when: "48 hours later", what: "Wait for payday, then charge the same card once. This step always runs." },
      { title: "Second retry", when: "96 hours later", what: "If still unpaid, try the same card once more. Skipped when P(recovery) is below 12%." },
      { title: "Last retry, then a text", when: "5 days later", what: "Final silent charge, then tell them. Also skipped when P is below 12%." },
      { title: "Send a payment link", when: "After the last retry", what: "Stop hitting this card. One link so they can pay another way." },
    ],
  },
  card_expired: {
    rule: "This card is dead. Never silent-retry it. Send a new way to pay.",
    steps: [
      { title: "Send a new payment link", when: "Now", what: "Ask for a new card. A retry on the expired one cannot work." },
      { title: "Text once", when: "After the link", what: "Remind them to open the link and update the card." },
      { title: "Text a second time", when: "After one more wait", what: "One last reminder. Then we stop." },
      { title: "Stop", when: "After the second text", what: "No more messages. No charge on the old card." },
    ],
  },
  payment_risk_check_failed: {
    rule: "Fraud or risk flagged this. We do not auto-charge. A person decides.",
    steps: [
      { title: "Do not charge", when: "Now", what: "Block any silent retry. Money stays put." },
      { title: "Send to the other person", when: "Now", what: "It sits on the policy queue until they look." },
      { title: "Wait for a yes or no", when: "Until they decide", what: "Nothing is charged while it waits." },
      { title: "Stay blocked", when: "If they do not release it", what: "If the score is high (55%+), they may release it. That is how recovered went up on the batch." },
    ],
  },
  "checkout.abandoned": {
    rule: "They left the page. One link. Do not charge the card in the background.",
    steps: [
      { title: "Send a payment link", when: "Now", what: "Let them finish checkout on their own." },
      { title: "Text once", when: "After the link", what: "Remind them the cart is still open." },
      { title: "Text a second time", when: "After one more wait", what: "One last reminder." },
      { title: "Stop", when: "After the second text", what: "We do not keep charging or messaging." },
    ],
  },
  "invoice.expired": {
    rule: "This is a company bill, not a card. Chase a promise to pay.",
    steps: [
      { title: "Ask for a promise to pay", when: "Now", what: "Ask for a date. Do not silent-retry a card." },
      { title: "Follow up once", when: "If they did not reply", what: "Email again for a date." },
      { title: "Hand to collections", when: "If still unpaid", what: "A person takes the chase from here." },
      { title: "Stop auto-chase", when: "After escalate", what: "The folder ends. No card retries." },
    ],
  },
  "subscription.pending": {
    rule: "The mandate missed. First retry always runs. Extra mandate tries can be skipped if P is low.",
    steps: [
      { title: "First mandate retry", when: "48 hours later", what: "Try the same mandate once. This step always runs." },
      { title: "Second mandate retry", when: "96 hours later", what: "One more try. Skipped when P is below 12%." },
      { title: "Last mandate retry", when: "5 days later", what: "Final debit before we warn them." },
      { title: "Warn by text", when: "After the last retry", what: "Tell them the subscription may halt next." },
    ],
  },
  "subscription.halted": {
    rule: "Retries already ran out. Ask them to set a new mandate. Do not silent-retry the old one.",
    steps: [
      { title: "Send an update link", when: "Now", what: "They must start a new mandate." },
      { title: "Text once", when: "After the link", what: "Ask them to open it and restart." },
      { title: "Text a second time", when: "After one more wait", what: "One last reminder." },
      { title: "Stop", when: "After the second text", what: "No more halt nudges." },
    ],
  },
  payment_cancelled: {
    rule: "They cancelled. We do not retry a cancelled charge.",
    steps: [
      { title: "Do not retry", when: "Now", what: "Cancelled stays cancelled." },
      { title: "Optional low-priority text", when: "Only if they still want to pay", what: "A soft ask. Not a charge." },
      { title: "Wait", when: "After that", what: "Do not chase hard." },
      { title: "Stop", when: "Then", what: "The folder ends." },
    ],
  },
  invalid_vpa: {
    rule: "The UPI address is wrong. Retrying the same VPA will fail.",
    steps: [
      { title: "Send a new payment link", when: "Now", what: "Ask for a new UPI or another method." },
      { title: "Text once", when: "After the link", what: "Ask for a valid VPA." },
      { title: "Text a second time", when: "After one more wait", what: "One last reminder." },
      { title: "Stop", when: "After the second text", what: "No more VPA nudges." },
    ],
  },
  gateway_technical: {
    rule: "The bank hiccuped. Wait, try the same method once, then change channel.",
    steps: [
      { title: "First retry", when: "48 hours later", what: "Same method, once. This step always runs." },
      { title: "Second retry", when: "96 hours later", what: "One more try if still unpaid." },
      { title: "Last retry", when: "5 days later", what: "Final debit attempt." },
      { title: "Send a payment link", when: "After the last retry", what: "Stop retrying. One link." },
    ],
  },
  bank_technical: {
    rule: "The bank hiccuped. Wait, try the same method once, then change channel.",
    steps: [
      { title: "First retry", when: "48 hours later", what: "Same method, once. This step always runs." },
      { title: "Second retry", when: "96 hours later", what: "One more try if still unpaid." },
      { title: "Last retry", when: "5 days later", what: "Final debit attempt." },
      { title: "Send a payment link", when: "After the last retry", what: "Stop retrying. One link." },
    ],
  },
};

function folderFor(reason: string) {
  const key = reason.toLowerCase();
  if (FOLDERS[key]) {
    return FOLDERS[key];
  }
  const hit = Object.entries(FOLDERS).find(([name]) => key.includes(name));
  return hit?.[1] ?? FOLDERS.insufficient_funds;
}

export function catalogPlaybook(reason: string): CatalogPlaybook {
  const folder = folderFor(reason);
  return {
    reason,
    why: reasonBlurb(reason),
    rule: folder.rule,
    steps: folder.steps,
  };
}
