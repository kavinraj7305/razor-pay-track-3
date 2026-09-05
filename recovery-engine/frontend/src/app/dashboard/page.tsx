"use client";

import { useCallback, useEffect, useState } from "react";
import { DeskChrome, RoleGate } from "@/components/DeskChrome";
import { adminBenchmark, adminDashboard, createDeskUser, inr, pct, prettyError } from "@/lib/api";
import type { BenchmarkReport, DashboardSnapshot } from "@/lib/types";

export default function DashboardPage() {
  return (
    <RoleGate allow={["ADMIN"]}>
      <DeskChrome
        kicker="CEO"
        title="Command dashboard"
        blurb="See the money, the 500-event baseline-vs-AI number, and add the one other person."
      >
        <AdminBody />
      </DeskChrome>
    </RoleGate>
  );
}

function AdminBody() {
  const [snap, setSnap] = useState<DashboardSnapshot | null>(null);
  const [bench, setBench] = useState<BenchmarkReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");

  const load = useCallback(async () => {
    const [nextSnap, nextBench] = await Promise.all([adminDashboard(), adminBenchmark()]);
    setSnap(nextSnap);
    setBench(nextBench);
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

      {bench ? <Scoreboard report={bench} /> : null}

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

function Scoreboard({ report }: { report: BenchmarkReport }) {
  return (
    <section className="scoreboard">
      <div className="ops-head">
        <div>
          <p className="pill">Baseline vs AI · 500 labelled events</p>
          <strong>{report.pitch}</strong>
        </div>
        <div className="safety-chips">
          <span className="chip">seed {report.seed}</span>
          <span className="chip">{report.merchantId}</span>
          {report.model.rocAuc != null ? <span className="chip">ROC-AUC {report.model.rocAuc}</span> : null}
        </div>
      </div>
      <div className="score-grid">
        <article className="score-col">
          <p className="pill">Reason playbook</p>
          <strong>{inr(report.baseline.recoveredInr)}</strong>
          <span className="muted">
            {pct(report.baseline.recoveryRate)} of {inr(report.amountAtRiskInr)} at risk · {report.baseline.recoveredCases}{" "}
            cases
          </span>
          <span className="muted">
            {report.baseline.wastedChases} wasted chases · {inr(report.baseline.wastedInr)} that never came back
          </span>
        </article>
        <article className="score-col ai">
          <p className="pill">Playbook + P + policy</p>
          <strong>{inr(report.ai.recoveredInr)}</strong>
          <span className="muted">
            {pct(report.ai.recoveryRate)} recovered · {report.ai.recoveredCases} cases
          </span>
          <span className="muted">
            {report.ai.wastedChases} wasted chases · {inr(report.ai.wastedInr)} doomed
          </span>
        </article>
        <article className="score-col lift">
          <p className="pill">What the model changed</p>
          <strong>{inr(report.wastedChaseSavedInr)}</strong>
          <span className="muted">
            doomed chase avoided · {report.wastedChasesAvoided} fewer wasted retries
          </span>
          <span className="muted">
            {report.mlSkipRetry} low-P skips · {report.policyBlocked} held for a human · recovered{" "}
            {inr(report.recoveredDeltaInr)} vs playbook
          </span>
        </article>
      </div>
      <p className="muted score-note">
        Oracle ceiling if every eventual payer came back: {inr(report.oracleRecoveredInr)} ({pct(report.oracleRate)}).
        Risk cases are not auto-chased on either path. Numbers came from{" "}
        <code>uv run python scripts/run_benchmark.py</code> — not a slide.
      </p>
      <div className="score-reasons">
        {report.byReason.map((row) => (
          <div key={row.reason} className="score-reason">
            <span className="reason">{prettyReason(row.reason)}</span>
            <span className="muted">{row.events}</span>
            <span>{inr(row.baselineRecoveredInr)}</span>
            <span>{inr(row.aiRecoveredInr)}</span>
          </div>
        ))}
      </div>
      {report.unresolved.length > 0 ? (
        <div>
          <p className="pill">Honest misses — AI skipped, label said they paid</p>
          {report.unresolved.map((row) => (
            <div key={row.eventId} className="score-miss">
              <span className="reason">{prettyReason(row.reason)}</span>
              <span>{inr(row.amountInr)}</span>
              <span className="muted">
                P={pct(row.pRecovery)} · {row.why.replaceAll("_", " ")}
              </span>
            </div>
          ))}
        </div>
      ) : null}
    </section>
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
