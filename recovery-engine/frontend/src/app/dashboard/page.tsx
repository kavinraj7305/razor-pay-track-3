"use client";

import { useCallback, useEffect, useState } from "react";
import { DeskChrome, RoleGate } from "@/components/DeskChrome";
import { adminBenchmark, adminDashboard, inr, pct, prettyError } from "@/lib/api";
import type { BenchmarkReport, DashboardSnapshot } from "@/lib/types";

export default function DashboardPage() {
  return (
    <RoleGate allow={["ADMIN"]}>
      <DeskChrome
        kicker="CEO"
        title="Recovery overview"
        blurb="Money at risk, why it is stuck, and the measured batch — more recovered rupees, fewer extra silent retries. The first payday try is cheap and always runs."
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

  const load = useCallback(async () => {
    const [nextSnap, nextBench] = await Promise.all([adminDashboard(), adminBenchmark()]);
    setSnap(nextSnap);
    setBench(nextBench);
  }, []);

  useEffect(() => {
    void load().catch((err) => setError(prettyError(err)));
  }, [load]);

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

      <section className="panel">
        <div className="panel-head">
          <h2>Why money is stuck</h2>
          <span className="muted">{snap?.cases ?? 0} open cases on the live book</span>
        </div>
        {(snap?.byReason ?? []).length === 0 ? (
          <p className="empty">No open cases on the live book yet.</p>
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
    </div>
  );
}

function Scoreboard({ report }: { report: BenchmarkReport }) {
  return (
    <section className="scoreboard">
      <div className="ops-head">
        <div>
          <p className="pill">Measured batch · 500 labelled failures</p>
          <strong>{report.pitch}</strong>
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
          <p className="pill">Playbook + first retry + P</p>
          <strong>{inr(report.ai.recoveredInr)}</strong>
          <span className="muted">
            {pct(report.ai.recoveryRate)} recovered · {report.ai.recoveredCases} cases
          </span>
          <span className="muted">
            First try always runs · {report.extraRetriesSkipped ?? report.mlSkipRetry * 2} extra silent
            retries skipped
          </span>
        </article>
        <article className="score-col lift">
          <p className="pill">What scoring changed</p>
          <strong>+{inr(report.recoveredDeltaInr)}</strong>
          <span className="muted">
            more recovered vs playbook · retries are cheap, so we do not drop first-try payers
          </span>
          <span className="muted">
            {report.extraRetriesSkipped ?? report.mlSkipRetry * 2} extra retries cut ·{" "}
            {report.highPHoldsReleased ?? 0} high-P holds released · {report.policyBlocked} still waiting
            on a person
          </span>
        </article>
      </div>
      <p className="muted score-note">
        If every customer who later paid had come back, the ceiling was {inr(report.oracleRecoveredInr)} (
        {pct(report.oracleRate)}). Risk is not auto-charged. High-P risk sits for the other person — those
        holds are how recovered went up. Extra silent retries are what we cut, not the first payday try.
      </p>
      <div className="score-reasons">
        <div className="score-reason head">
          <span>Failure</span>
          <span>Cases</span>
          <span>Playbook</span>
          <span>With scoring</span>
        </div>
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
          <p className="pill">Not chased — later paid</p>
          {report.unresolved.map((row) => (
            <div key={row.eventId} className="score-miss">
              <span className="reason">{prettyReason(row.reason)}</span>
              <span>{inr(row.amountInr)}</span>
              <span className="muted">
                P={pct(row.pRecovery)} · {row.why.replaceAll("_", " ").toLowerCase()}
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
