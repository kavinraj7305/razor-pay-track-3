"use client";

import { useCallback, useEffect, useState } from "react";
import { DeskChrome, RoleGate } from "@/components/DeskChrome";
import { adminDashboard, createDeskUser, inr, prettyError } from "@/lib/api";
import type { DashboardSnapshot } from "@/lib/types";

export default function DashboardPage() {
  return (
    <RoleGate allow={["ADMIN"]}>
      <DeskChrome
        kicker="CEO"
        title="Command dashboard"
        blurb="See the money, run the desk, and add the one other person — the human in the loop."
      >
        <AdminBody />
      </DeskChrome>
    </RoleGate>
  );
}

function AdminBody() {
  const [snap, setSnap] = useState<DashboardSnapshot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");

  const load = useCallback(async () => {
    setSnap(await adminDashboard());
  }, []);

  useEffect(() => {
    void load().catch((err) => setError(prettyError(err)));
  }, [load]);

  async function run(work: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await work();
      await load();
    } catch (err) {
      setError(prettyError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="wrap">
      {error ? <div className="err">{error}</div> : null}
      <section className="stat-grid">
        <article className="stat">
          <p className="pill">Amount at risk</p>
          <strong>{inr(snap?.amountAtRisk)}</strong>
          <span className="muted">{snap?.open ?? 0} open cases</span>
        </article>
        <article className="stat">
          <p className="pill">Waiting on policy</p>
          <strong>{snap?.pendingApprovals ?? "—"}</strong>
          <span className="muted">Human-in-the-loop queue</span>
        </article>
        <article className="stat">
          <p className="pill">Recovered</p>
          <strong>{snap?.recovered ?? "—"}</strong>
          <span className="muted">{snap?.failed ?? 0} failed / expired</span>
        </article>
        <article className="stat">
          <p className="pill">People</p>
          <strong>
            {snap?.adminCount ?? 0} · {snap?.approverCount ?? 0}
          </strong>
          <span className="muted">CEO · human in the loop</span>
        </article>
      </section>

      <div className="desk-split">
        <section className="panel">
          <div className="panel-head">
            <h2>Why money is stuck</h2>
            <span className="muted">Hover a row · {snap?.cases ?? 0} cases</span>
          </div>
          {(snap?.byReason ?? []).length === 0 ? (
            <p className="empty">No cases yet. Open the recovery desk and create a pack.</p>
          ) : (
            <div className="rows">
              {snap?.byReason.map((row) => (
                <div key={row.reason} className="stuck-row" tabIndex={0}>
                  <div className="stuck-main">
                    <span className="reason">{prettyReason(row.reason)}</span>
                    <span className="stuck-count">{row.count}</span>
                  </div>
                  <p className="stuck-tip">{reasonBlurb(row.reason)}</p>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="panel">
          <div className="panel-head">
            <h2>Add the other person</h2>
            <span className="muted">Human in the loop — signs off blocked cases</span>
          </div>
          <form
            className="form-grid pad"
            onSubmit={(event) => {
              event.preventDefault();
              void run(async () => {
                await createDeskUser({ email, displayName, password, role: "APPROVER" });
                setEmail("");
                setDisplayName("");
                setPassword("");
              });
            }}
          >
            <label>
              Name
              <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} required />
            </label>
            <label>
              Email
              <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
            </label>
            <label>
              Temporary password
              <input value={password} onChange={(event) => setPassword(event.target.value)} required />
            </label>
            <button className="primary-btn" type="submit" disabled={busy}>
              {busy ? "Saving…" : "Add human in the loop"}
            </button>
          </form>
        </section>
      </div>

      <section className="panel">
        <div className="panel-head">
          <h2>Directory</h2>
          <span className="muted">Two seats</span>
        </div>
        {(snap?.users ?? [])
          .filter((user) => user.active && user.role !== "OPERATOR")
          .map((user) => (
          <div key={user.userId} className="user-row">
            <div>
              <strong>{user.displayName}</strong>
              <span className="muted">
                {user.email} · {user.role === "ADMIN" ? "CEO" : "Human in the loop"}
              </span>
            </div>
            <span className="badge">{user.role === "ADMIN" ? "CEO" : "In the loop"}</span>
          </div>
        ))}
      </section>
    </div>
  );
}

function prettyReason(reason: string) {
  return reason.replaceAll("_", " ").replaceAll(".", " ");
}

const REASON_BLURBS: Record<string, string> = {
  insufficient_funds:
    "The bank said there wasn’t enough money. We wait for payday and try once — not in a loop.",
  card_expired: "The saved card is dead. A retry on the same card will fail, so we send a new payment link.",
  payment_risk_check_failed:
    "Fraud or risk checks blocked the charge. Money stays put until the human in the loop signs off.",
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

function reasonBlurb(reason: string) {
  const key = reason.toLowerCase();
  if (REASON_BLURBS[key]) {
    return REASON_BLURBS[key];
  }
  const match = Object.entries(REASON_BLURBS).find(([name]) => key.includes(name));
  if (match) {
    return match[1];
  }
  return "This failure is sitting open. Open the recovery desk if you want the playbook for it.";
}
