"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  createAllIssues,
  createIssue,
  executeNext,
  getCase,
  inr,
  listCases,
  listScenarios,
  opsBriefing,
  pct,
  proposeCase,
  recordAgentProposal,
  webhookInbox,
} from "@/lib/api";
import {
  latestActionForStep,
  outcomeFor,
  storyFor,
  type LogLine,
} from "@/lib/narrative";
import { completedSteps, progressLabel, remainingSteps, sleep } from "@/lib/progress";
import type {
  CaseDetail,
  CaseProposal,
  CaseSummary,
  OpsBriefing,
  Scenario,
  WebhookInboxSnapshot,
} from "@/lib/types";

type Phase = "idle" | "detect" | "score" | "act" | "done";

function scoreClass(value: number | null) {
  if (value == null) {
    return "score score-na";
  }
  return value < 0.25 ? "score low" : "score high";
}

function scoreLabel(row: Pick<CaseSummary, "recoveryProbability" | "scoreStatus">) {
  if (row.scoreStatus === "LOW_DATA") {
    return "playbook";
  }
  if (row.scoreStatus === "UNAVAILABLE") {
    return "no ML";
  }
  return pct(row.recoveryProbability);
}

function phaseState(current: Phase, name: Phase): "done" | "active" | "" {
  const order: Phase[] = ["detect", "score", "act", "done"];
  const here = order.indexOf(name);
  const now = order.indexOf(current === "idle" ? "detect" : current);
  if (current === "idle") {
    return "";
  }
  if (here < now) {
    return "done";
  }
  if (here === now) {
    return "active";
  }
  return "";
}

function actionWords(value: string) {
  return value.split("_").join(" ");
}

function proposalFromAudit(detail: CaseDetail | null): CaseProposal | null {
  const line = [...(detail?.audit ?? [])].reverse().find((item) => item.eventType === "AGENT_PROPOSE");
  const raw = line?.details;
  if (!raw || typeof raw.recommendedAction !== "string") {
    return null;
  }
  return {
    caseId: typeof raw.caseId === "string" ? raw.caseId : detail?.caseId ?? null,
    diagnosis: String(raw.diagnosis ?? "UNKNOWN_FAILURE"),
    reasoning: String(raw.reasoning ?? raw.reason ?? ""),
    recommendedAction: String(raw.recommendedAction),
    defaultPlaybookAction: String(raw.defaultPlaybookAction ?? ""),
    deviatesFromPlaybook: Boolean(raw.deviatesFromPlaybook),
    confidence: Number(raw.confidence ?? 0),
    mlScore: raw.mlScore == null ? null : Number(raw.mlScore),
    escalate: Boolean(raw.escalate),
    actionsAvailable: Array.isArray(raw.actionsAvailable) ? raw.actionsAvailable.map(String) : ["propose"],
    executes: false,
    model: String(raw.model ?? "fallback-rules"),
    fallbackUsed: Boolean(raw.fallbackUsed),
  };
}

function auditDetail(line: { eventType: string; details: Record<string, unknown> | null }) {
  const details = line.details;
  if (!details) {
    return "";
  }
  if (line.eventType === "AGENT_PROPOSE") {
    return `${String(details.recommendedAction ?? "")} · escalate=${String(details.escalate ?? false)}`;
  }
  if (line.eventType.startsWith("POLICY_")) {
    return `${String(details.verdict ?? "")} · ${String(details.reason ?? "")}`;
  }
  if (typeof details.note === "string") {
    return details.note;
  }
  return "";
}

function stepState(detail: CaseDetail, step: number, runningStep: number | null) {
  if (runningStep === step) {
    return "running";
  }
  if (completedSteps(detail).has(step)) {
    return "done";
  }
  return "pending";
}

export function RecoveryDesk() {
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [cases, setCases] = useState<CaseSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<CaseDetail | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [phase, setPhase] = useState<Phase>("idle");
  const [ticker, setTicker] = useState("Select a case, then start the live recovery process.");
  const [runningStep, setRunningStep] = useState<number | null>(null);
  const [scorePop, setScorePop] = useState(false);
  const [waitPct, setWaitPct] = useState(0);
  const [waitClock, setWaitClock] = useState<string | null>(null);
  const [whatNow, setWhatNow] = useState("Nothing running yet.");
  const [whyNow, setWhyNow] = useState("Press Start to simulate the real playbook timeline (compressed).");
  const [outcomeNow, setOutcomeNow] = useState<{ label: string; tone: string; detail: string } | null>(
    null,
  );
  const [log, setLog] = useState<LogLine[]>([]);
  const [proposal, setProposal] = useState<CaseProposal | null>(null);
  const [briefing, setBriefing] = useState<OpsBriefing | null>(null);
  const [inbox, setInbox] = useState<WebhookInboxSnapshot | null>(null);
  const runToken = useRef(0);

  const loadBriefing = useCallback(async () => {
    try {
      setBriefing(await opsBriefing(6));
    } catch {
      setBriefing(null);
    }
  }, []);

  const loadInbox = useCallback(async () => {
    try {
      setInbox(await webhookInbox());
    } catch {
      setInbox(null);
    }
  }, []);

  const pushLog = useCallback((line: Omit<LogLine, "id">) => {
    setLog((prev) => [{ ...line, id: `${Date.now()}-${prev.length}` }, ...prev].slice(0, 24));
  }, []);

  const refresh = useCallback(async (keepId?: string | null) => {
    const rows = await listCases();
    setCases(rows);
    const nextId = keepId && rows.some((row) => row.caseId === keepId) ? keepId : rows[0]?.caseId ?? null;
    setSelectedId(nextId);
    if (nextId) {
      setDetail(await getCase(nextId));
    } else {
      setDetail(null);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const catalog = await listScenarios();
        if (!cancelled) {
          setScenarios(catalog);
        }
        await refresh();
        void loadBriefing();
        void loadInbox();
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Backend is not reachable on :8080");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [refresh, loadBriefing, loadInbox]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      void loadInbox();
    }, 4000);
    return () => window.clearInterval(timer);
  }, [loadInbox]);

  async function run(label: string, work: () => Promise<void>) {
    setBusy(label);
    setError(null);
    try {
      await work();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Request failed");
    } finally {
      setBusy(null);
    }
  }

  async function animateWait(ms: number, token: number) {
    const started = Date.now();
    setWaitPct(0);
    while (Date.now() - started < ms) {
      if (runToken.current !== token) {
        return false;
      }
      setWaitPct(Math.min(100, Math.round(((Date.now() - started) / ms) * 100)));
      await sleep(80);
    }
    setWaitPct(100);
    return true;
  }

  async function startLiveProcess() {
    if (!detail) {
      return;
    }
    const token = ++runToken.current;
    setBusy("live");
    setError(null);
    setScorePop(false);
    setOutcomeNow(null);
    setLog([]);
    setWaitPct(0);
    setWaitClock(null);

    try {
      let current = detail;
      const hasProposal = current.audit?.some((line) => line.eventType === "AGENT_PROPOSE");
      if (!hasProposal) {
        setTicker("Asking agent first — policy needs a proposal on the audit trail.");
        setWhatNow("Java will not execute until the agent proposal is stored.");
        setWhyNow("PolicyEngine reads AGENT_PROPOSE from audit_event, then allows / skips / blocks.");
        const next = await proposeCase(current.caseId);
        setProposal(next);
        current = await recordAgentProposal(current.caseId, next);
        setDetail(current);
        pushLog({
          clock: "T+0",
          title: "Agent propose stored",
          body: `${next.recommendedAction} · escalate=${String(next.escalate)}`,
          tone: "info",
        });
      }

      setPhase("detect");
      setTicker("Failure detected — opening recovery case.");
      setWhatNow("Webhook / simulate event created a RecoveryCase.");
      setWhyNow("Money is at risk. We diagnose by failure reason before any charge.");
      pushLog({
        clock: "T+0",
        title: "Detected",
        body: `${detail.reason} · ${inr(detail.amountAtRisk)} at risk`,
        tone: "info",
      });
      await sleep(1000);
      if (runToken.current !== token) {
        return;
      }

      setPhase("score");
      setScorePop(true);
      const probability = detail.score?.recoveryProbability ?? detail.recoveryProbability;
      setTicker(
        probability == null
          ? "Scoring skipped — ML down. Playbook still runs."
          : `Scoring this customer — P(recovery) = ${pct(probability)}`,
      );
      setWhatNow("ML reads payment history, LTV, delays, and this failure.");
      setWhyNow(
        probability == null
          ? "Without a score we still follow the reason playbook safely."
          : "Same NSF reason can get different chase intensity for good vs bad payers.",
      );
      pushLog({
        clock: "T+0",
        title: "Scored",
        body:
          probability == null
            ? "ML unavailable — playbook only"
            : `P(recovery)=${pct(probability)} (${detail.score?.status ?? "SCORED"})`,
        tone: "info",
      });
      await sleep(1400);
      if (runToken.current !== token) {
        return;
      }

      setPhase("act");
      current = await getCase(current.caseId);
      setDetail(current);

      if (current.status === "RECOVERED") {
        setPhase("done");
        setTicker("Already recovered — process stops.");
        setWhatNow("Payment captured / case closed.");
        setWhyNow("No more retries.");
        return;
      }

      // Snapshot remaining steps once — each step runs at most one wait + one execute.
      const queue = remainingSteps(current);
      const ran = new Set<number>();

      for (const next of queue) {
        if (runToken.current !== token) {
          return;
        }
        if (current.status === "RECOVERED") {
          break;
        }
        if (ran.has(next.step) || completedSteps(current).has(next.step)) {
          continue;
        }
        ran.add(next.step);

        const probabilityNow = current.score?.recoveryProbability ?? current.recoveryProbability;
        const story = storyFor(current.reason, next, probabilityNow);
        setRunningStep(next.step);
        setWaitClock(story.clockLabel);
        setTicker(story.waitLabel);
        setWhatNow(story.what);
        setWhyNow(story.why);
        setOutcomeNow(null);
        pushLog({
          clock: story.clockLabel,
          title: `Wait once · step ${next.step}`,
          body: `${story.waitLabel} — then one execute, not a loop`,
          tone: "wait",
        });

        const ok = await animateWait(story.waitMs, token);
        if (!ok) {
          return;
        }

        setTicker(`One execute · step ${next.step}: ${next.actionType.split("_").join(" ")}`);
        setWhatNow(`Calling Java /execute once for step ${next.step}.`);
        setWhyNow("Wait is simulated. Java runs this step a single time, then we move to the next step.");

        const doneBefore = completedSteps(current).size;
        try {
          current = await executeNext(current.caseId);
          if (current.policy?.verdict === "BLOCK") {
            setDetail(current);
            setRunningStep(null);
            setWaitClock(null);
            setTicker("Stopped — policy guard must approve this case.");
            setWhatNow("PolicyEngine blocked execute. The human-in-the-loop person reviews it next.");
            setWhyNow(`${current.policy.reason} · waiting in the policy queue.`);
            setOutcomeNow({
              label: "Waiting for approval",
              tone: "stop",
              detail: "This case waits for the human in the loop. After they sign off, Start can continue.",
            });
            pushLog({
              clock: story.clockLabel,
              title: "Policy blocked",
              body: current.policy.reason,
              tone: "stop",
            });
            return;
          }
        } catch (err) {
          const message = err instanceof Error ? err.message : "Execute failed";
          setError(message);
          setTicker("Stopped — execute failed.");
          setWhatNow(message);
          setWhyNow(
            message.includes("no playbook")
              ? "This reason has no 4-step execute path yet. Use insufficient_funds or risk-failed for the pitch."
              : "Backend rejected the step.",
          );
          setRunningStep(null);
          setWaitClock(null);
          return;
        }

        setDetail(current);
        const finished = latestActionForStep(current, next.step);
        const progressed = completedSteps(current).size > doneBefore || Boolean(finished);
        const outcome = progressed
          ? outcomeFor(finished, current.reason)
          : {
              label: "Skipped — no repeat",
              tone: "stop" as const,
              detail:
                "Java did not advance this step (policy/ML skip). Stopping so T+96h does not run again.",
            };
        setOutcomeNow(outcome);
        setRunningStep(null);
        setTicker(`Step ${next.step} finished once · ${outcome.label}`);
        setWhatNow(outcome.detail);
        setWhyNow(story.why);
        pushLog({
          clock: story.clockLabel,
          title: `${outcome.label} · step ${next.step}`,
          body: outcome.detail,
          tone: outcome.tone === "fail" ? "fail" : outcome.tone === "stop" ? "stop" : "ok",
        });

        if (!progressed) {
          break;
        }

        await sleep(900);
      }

      if (runToken.current !== token) {
        return;
      }
      setPhase("done");
      setWaitClock(null);
      setWaitPct(100);
      setTicker(
        current.status === "RECOVERED"
          ? "Done — revenue recovered."
          : "Done — playbook finished for this run (may still be unpaid).",
      );
      setWhatNow(
        current.status === "RECOVERED"
          ? "Case closed as recovered."
          : "All planned steps ran. DEV retries often fail on purpose so you can see the full chain.",
      );
      setWhyNow("Judges should see: wait → attempt → fail/succeed → next channel → audit.");
      pushLog({
        clock: "End",
        title: "Process finished",
        body: progressLabel(current),
        tone: "info",
      });
      await refresh(current.caseId);
    } finally {
      if (runToken.current === token) {
        setBusy(null);
        setRunningStep(null);
      }
    }
  }

  const total = detail?.playbook?.length ?? 0;
  const doneCount = detail ? completedSteps(detail).size : 0;
  const meterPct = total === 0 ? 0 : Math.min(100, Math.round((doneCount / total) * 100));
  const canStart =
    detail != null &&
    busy === null &&
    detail.status !== "RECOVERED" &&
    (detail.playbook?.length ?? 0) > 0;
  const canAsk = detail != null && busy === null;

  async function askAgent() {
    if (!detail) {
      return;
    }
    await run("agent", async () => {
      const caseId = detail.caseId;
      const next = await proposeCase(caseId);
      setProposal(next);
      const stored = await recordAgentProposal(caseId, next);
      setDetail(stored);
      setTicker(
        next.deviatesFromPlaybook
          ? `Agent deviates: ${actionWords(next.recommendedAction)} (playbook: ${actionWords(next.defaultPlaybookAction)})`
          : `Agent agrees with playbook: ${actionWords(next.recommendedAction)}`,
      );
      setWhatNow(next.diagnosis.replaceAll("_", " "));
      setWhyNow(next.reasoning);
    });
  }

  return (
    <div>
      <div className="wrap">
        <div className="desk-actions">
          <button
            className="primary-btn"
            disabled={busy !== null}
            onClick={() =>
              run("all", async () => {
                await createAllIssues();
                await refresh(selectedId);
                await loadBriefing();
                await loadInbox();
              })
            }
          >
            {busy === "all" ? "Creating pack…" : "Create all 8 issues"}
          </button>
        </div>
        {error ? <div className="err">{error}</div> : null}

        <section className="ops-strip">
          <div className="ops-head">
            <div>
              <p className="pill">Ops patterns · last 6h</p>
              <strong>{briefing?.summary ?? "Agent service not reachable on :8002 — SQL briefing skipped."}</strong>
            </div>
            <div className="safety-chips">
              <span className="chip">actions_available: propose only</span>
              {briefing?.fallbackUsed ? <span className="chip warn">fallback_used</span> : null}
              {briefing ? <span className="chip">{briefing.model}</span> : null}
            </div>
          </div>
          {briefing && briefing.patterns.length === 0 ? (
            <p className="muted">No recurring spikes in this window.</p>
          ) : null}
          {briefing?.patterns.map((item) => (
            <article key={`${item.pattern}-${item.where}`} className={`ops-alert ${item.severity.toLowerCase()}`}>
              <span className="badge">{item.severity}</span>
              <div>
                <strong>{item.pattern.replaceAll("_", " ")}</strong>
                <p>
                  {item.why} · {item.proposedSolution}
                </p>
                <span className="muted">
                  {item.where} · {item.count} cases
                </span>
              </div>
            </article>
          ))}
        </section>

        <section className="hmac-strip">
          <div className="ops-head">
            <div>
              <p className="pill">Live signed intake · POST /webhooks/razorpay</p>
              <strong>
                {inbox == null
                  ? "Inbox not loaded yet."
                  : inbox.razorpayCount > 0
                    ? `${inbox.razorpayCount} HMAC event${inbox.razorpayCount === 1 ? "" : "s"} from Razorpay Test Mode.`
                    : inbox.signedCount > 0
                      ? `${inbox.signedCount} HMAC event${inbox.signedCount === 1 ? "" : "s"} — signed locally, not from Razorpay servers.`
                      : "No HMAC-signed webhook yet. Desk buttons skip this path."}
              </strong>
            </div>
            <div className="safety-chips">
              <span className="chip">HMAC-SHA256</span>
              {inbox ? <span className="chip">signed {inbox.signedCount}</span> : null}
              {inbox?.razorpayCount ? <span className="chip go">razorpay {inbox.razorpayCount}</span> : null}
            </div>
          </div>
          {inbox && inbox.events.length === 0 ? (
            <p className="muted">
              Run <code>scripts/razorpay/prove-live-webhook.ps1</code>, then Send Test Webhook in the
              Razorpay dashboard or fail the payment link. This strip polls every 4s.
            </p>
          ) : null}
          {inbox?.events.slice(0, 4).map((item) => (
            <article
              key={item.eventId}
              className={`hmac-row ${item.origin === "RAZORPAY" ? "live" : item.signatureVerified ? "signed" : ""}`}
            >
              <span className={`badge ${item.origin === "RAZORPAY" ? "go" : ""}`}>
                {item.origin === "RAZORPAY"
                  ? "Razorpay HMAC"
                  : item.origin === "LOCAL_SCRIPT"
                    ? "Local HMAC"
                    : "Desk simulate"}
              </span>
              <div>
                <strong>{item.eventType}</strong>
                <p>
                  {item.eventId}
                  {item.caseId ? ` · case ${item.caseId.slice(0, 14)}…` : ""}
                  {item.reason ? ` · ${item.reason}` : ""}
                </p>
                <span className="muted">
                  {item.accountId ?? "no account"} · signature {item.signatureVerified ? "ok" : "skipped"} ·{" "}
                  {item.processed ? "ingested" : "waiting on Kafka"}
                </span>
              </div>
            </article>
          ))}
        </section>

        <section>
          <p className="muted" style={{ marginBottom: "0.5rem" }}>
            Pitch tip: create <strong>insufficient_funds</strong> (retries fail → pay link) or{" "}
            <strong>risk-failed</strong> (blocked).
          </p>
          <div className="create-grid">
            {scenarios.map((scenario) => (
              <button
                key={scenario.slug}
                className="issue-btn"
                disabled={busy !== null}
                onClick={() =>
                  run(scenario.slug, async () => {
                    runToken.current += 1;
                    setPhase("idle");
                    setRunningStep(null);
                    setLog([]);
                    setOutcomeNow(null);
                    setWaitClock(null);
                    setTicker("Case created. Ask the agent, or start the recovery process.");
                    setWhatNow("Case is ready.");
                    setWhyNow("Agent proposes only. Start still runs Java execute.");
                    setProposal(null);
                    const created = await createIssue(scenario.slug);
                    await refresh(created.caseId);
                    await loadBriefing();
                    await loadInbox();
                  })
                }
              >
                {scenario.reason}
                <small>{scenario.intendedAction}</small>
              </button>
            ))}
          </div>
        </section>

        <div className="desk-split">
          <section className="panel">
            <div className="panel-head">
              <h2>Open issues</h2>
              <span className="muted">{cases.length} demo cases</span>
            </div>
            {cases.length === 0 ? (
              <p className="empty">No demo cases yet. Create one above.</p>
            ) : (
              <div className="rows">
                {cases.map((row) => (
                  <button
                    key={row.caseId}
                    className={row.caseId === selectedId ? "case-row active" : "case-row"}
                    onClick={() =>
                      run("open", async () => {
                        runToken.current += 1;
                        setPhase("idle");
                        setRunningStep(null);
                        setLog([]);
                        setOutcomeNow(null);
                        setWaitClock(null);
                        setTicker("Case selected. Ask the agent, or start the recovery process.");
                        setWhatNow("Ready to simulate.");
                        setWhyNow("Agent proposes. Java executes.");
                        const opened = await getCase(row.caseId);
                        setProposal(proposalFromAudit(opened));
                        setSelectedId(row.caseId);
                        setDetail(opened);
                      })
                    }
                  >
                    <span className={scoreClass(row.recoveryProbability)}>{scoreLabel(row)}</span>
                    <span>
                      <span className="reason">{row.reason}</span>
                      <span className="muted">
                        {row.status} · {row.source}
                      </span>
                      <span className="steps">
                        {row.playbook.slice(0, 4).map((step) => (
                          <span key={step.step} className="chip">
                            {step.step}. {step.actionType.split("_").join(" ")}
                          </span>
                        ))}
                      </span>
                    </span>
                    <span>{inr(row.amountAtRisk)}</span>
                    <span className="muted">{row.actionStatus ?? "—"}</span>
                  </button>
                ))}
              </div>
            )}
          </section>

          <aside className="panel">
            <div className="panel-head">
              <h2>Live process</h2>
              <span className="muted">{detail ? progressLabel(detail) : "—"}</span>
            </div>
            {!detail ? (
              <p className="empty">Select a case to start the live demo.</p>
            ) : (
              <div className="detail">
                <div className="pipeline">
                  <div className={`phase ${phaseState(phase, "detect")}`}>
                    Detect
                    <strong>Case open</strong>
                  </div>
                  <div className={`phase ${phaseState(phase, "score")}`}>
                    Score
                    <strong>P(recovery)</strong>
                  </div>
                  <div className={`phase ${phaseState(phase, "act")}`}>
                    Act
                    <strong>Playbook</strong>
                  </div>
                  <div className={`phase ${phaseState(phase, "done")}`}>
                    Done
                    <strong>Audit</strong>
                  </div>
                </div>

                <div className="live-bar">
                  <div style={{ flex: 1 }}>
                    <div className="ticker">{ticker}</div>
                    <div className="meter">
                      <span style={{ width: `${phase === "done" ? 100 : meterPct}%` }} />
                    </div>
                  </div>
                  <span className="muted" style={{ whiteSpace: "nowrap" }}>
                    {doneCount}/{total || "—"}
                  </span>
                </div>

                {waitClock ? (
                  <div className="wait-card">
                    <div>
                      <p className="pill" style={{ color: "var(--navy)" }}>
                        Simulated real-world wait
                      </p>
                      <strong className="display" style={{ fontSize: "1.35rem" }}>
                        {waitClock}
                      </strong>
                      <span className="muted">Compressed for the demo — not a real 48h pause.</span>
                    </div>
                    <div className="wait-ring" style={{ ["--p" as string]: `${waitPct}%` }}>
                      <span>{waitPct}%</span>
                    </div>
                  </div>
                ) : null}

                <div className="action-row">
                  <button
                    className="ghost-btn ask-btn"
                    disabled={!canAsk}
                    onClick={() => void askAgent()}
                  >
                    {busy === "agent" ? "Asking agent…" : "Ask agent"}
                  </button>
                  <button
                    className={busy === "live" ? "start-btn running" : "start-btn"}
                    disabled={!canStart}
                    onClick={() => void startLiveProcess()}
                  >
                    {busy === "live"
                      ? "Simulating recovery…"
                      : detail.status === "RECOVERED"
                        ? "Process finished"
                        : "Start recovery process"}
                  </button>
                </div>

                <div className="explain">
                  <div>
                    <p className="pill" style={{ color: "var(--navy)" }}>
                      What is happening
                    </p>
                    <p>{whatNow}</p>
                  </div>
                  <div>
                    <p className="pill" style={{ color: "var(--navy)" }}>
                      Why
                    </p>
                    <p>{whyNow}</p>
                  </div>
                  {outcomeNow ? (
                    <div className={`outcome ${outcomeNow.tone}`}>
                      <p className="pill">Outcome</p>
                      <strong>{outcomeNow.label}</strong>
                      <p>{outcomeNow.detail}</p>
                    </div>
                  ) : null}
                </div>

                <div className={`score-card ${scorePop || phase === "score" ? "highlight" : ""}`}>
                  <div className="big">{pct(detail.score?.recoveryProbability ?? detail.recoveryProbability)}</div>
                  <div>
                    <strong>{detail.reason}</strong>
                    <p className="muted" style={{ color: "#c9d4e3" }}>
                      P(recovery) = will this customer likely pay eventually?
                    </p>
                    <p className="muted" style={{ color: "#c9d4e3" }}>
                      {inr(detail.amountAtRisk)} · {detail.caseId}
                    </p>
                  </div>
                </div>

                {detail.policy ? (
                  <div className={`policy-card ${detail.policy.verdict.toLowerCase()}`}>
                    <p className="pill" style={{ color: "var(--navy)" }}>
                      Policy engine
                    </p>
                    <strong>
                      {detail.policy.reason === "HUMAN_OVERRIDE"
                        ? "Policy guard approved — execute allowed"
                        : detail.policy.verdict === "BLOCK"
                          ? "Block execute — send to policy queue"
                          : detail.policy.verdict === "SKIP_RETRY"
                            ? "Skip extra retries — playbook may continue"
                            : "Allow playbook execute"}
                    </strong>
                    <p>
                      {actionWords(detail.policy.recommendedAction || "DELAYED_RETRY")} · {detail.policy.reason}
                      {detail.policy.escalate ? " · escalate" : ""}
                    </p>
                  </div>
                ) : null}

                {proposal ? (
                  <div className="agent-card">
                    <div className="panel-head" style={{ padding: 0, border: 0 }}>
                      <h3 className="display" style={{ margin: 0, fontSize: "1.05rem" }}>
                        Agent proposal
                      </h3>
                      <span className={proposal.deviatesFromPlaybook ? "badge deviate" : "badge agree"}>
                        {proposal.deviatesFromPlaybook ? "Deviates" : "Agrees with playbook"}
                      </span>
                    </div>
                    <p className="pill" style={{ color: "var(--navy)" }}>
                      {proposal.diagnosis.replaceAll("_", " ")}
                    </p>
                    <p>{proposal.reasoning}</p>
                    <dl className="agent-compare">
                      <div>
                        <dt>Recommended</dt>
                        <dd>{actionWords(proposal.recommendedAction)}</dd>
                      </div>
                      <div>
                        <dt>Playbook default</dt>
                        <dd>{actionWords(proposal.defaultPlaybookAction)}</dd>
                      </div>
                      <div>
                        <dt>Confidence · P(recovery)</dt>
                        <dd>
                          {pct(proposal.confidence)} · {pct(proposal.mlScore)}
                        </dd>
                      </div>
                      <div>
                        <dt>Escalate</dt>
                        <dd>{proposal.escalate ? "Yes — human" : "No"}</dd>
                      </div>
                    </dl>
                    <div className="safety-chips">
                      <span className="chip">actions_available: {(proposal.actionsAvailable ?? ["propose"]).join(", ")}</span>
                      <span className="chip">executes: {String(proposal.executes)}</span>
                      {proposal.fallbackUsed ? <span className="chip warn">fallback_used</span> : null}
                      <span className="chip">{proposal.model}</span>
                    </div>
                  </div>
                ) : (
                  <p className="muted">Ask agent for a diagnosis. It cannot charge — Java / Start still executes.</p>
                )}

                <div className="playbook">
                  <h3 className="display" style={{ margin: "0 0 0.6rem", fontSize: "1.05rem" }}>
                    Playbook progress
                  </h3>
                  <ol>
                    {(detail.playbook ?? []).map((step) => {
                      const state = stepState(detail, step.step, runningStep);
                      const finished = latestActionForStep(detail, step.step);
                      return (
                        <li key={step.step} className={state}>
                          <span className="n">{step.step}</span>
                          <div>
                            <strong>{step.actionType.split("_").join(" ")}</strong>
                            <span className="muted">{step.note}</span>
                            {finished ? (
                              <span className="muted">Result: {finished.status}</span>
                            ) : null}
                          </div>
                          <span className="badge">
                            {state === "running" ? "now" : state === "done" ? "done" : "wait"}
                          </span>
                        </li>
                      );
                    })}
                  </ol>
                </div>

                <div className="story">
                  <strong>Story log</strong>
                  {log.length === 0 ? (
                    <span className="muted">Events appear here as the simulation runs.</span>
                  ) : (
                    log.map((line) => (
                      <div key={line.id} className={`story-line ${line.tone}`}>
                        <span>{line.clock}</span>
                        <div>
                          <strong>{line.title}</strong>
                          <p>{line.body}</p>
                        </div>
                      </div>
                    ))
                  )}
                </div>

                <div className="audit">
                  <strong>Audit so far</strong>
                  {(detail.audit ?? []).length === 0 ? (
                    <span className="muted">No audit rows yet — Ask agent stores AGENT_PROPOSE, then policy writes BLOCK / SKIP.</span>
                  ) : (
                    [...detail.audit].reverse().map((line) => (
                      <div key={line.eventId} className={`audit-line ${line.eventType.startsWith("POLICY_") ? "policy" : ""}`}>
                        <span>
                          {line.eventType} · {line.action}
                        </span>
                        {auditDetail(line) ? <span className="muted">{auditDetail(line)}</span> : null}
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}
          </aside>
        </div>
      </div>
    </div>
  );
}
