"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  createIssue,
  executeNext,
  getCase,
  inr,
  listCases,
  listScenarios,
  proposeCase,
  recordAgentProposal,
} from "@/lib/api";
import {
  actionLabel,
  auditTitle,
  chanceLabel,
  policyBanner,
  prettyWords,
  reasonBlurb,
  statusLabel,
  stepResult,
} from "@/lib/copy";
import { latestActionForStep, outcomeFor, storyFor } from "@/lib/narrative";
import { completedSteps, remainingSteps, sleep } from "@/lib/progress";
import type { CaseDetail, CaseProposal, CaseSummary, Scenario } from "@/lib/types";

export function RecoveryDesk() {
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [newSlug, setNewSlug] = useState("");
  const [cases, setCases] = useState<CaseSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<CaseDetail | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [ticker, setTicker] = useState("Pick a case, then start recovery.");
  const [runningStep, setRunningStep] = useState<number | null>(null);
  const [waitPct, setWaitPct] = useState(0);
  const [waitClock, setWaitClock] = useState<string | null>(null);
  const [outcomeNow, setOutcomeNow] = useState<{ label: string; tone: string; detail: string } | null>(
    null,
  );
  const [proposal, setProposal] = useState<CaseProposal | null>(null);
  const runToken = useRef(0);

  const refresh = useCallback(async (keepId?: string | null) => {
    const rows = await listCases();
    setCases(rows);
    const nextId = keepId && rows.some((row) => row.caseId === keepId) ? keepId : rows[0]?.caseId ?? null;
    setSelectedId(nextId);
    if (nextId) {
      const opened = await getCase(nextId);
      setDetail(opened);
      setProposal(proposalFromAudit(opened));
    } else {
      setDetail(null);
      setProposal(null);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const catalog = await listScenarios();
        if (!cancelled) {
          setScenarios(catalog);
          setNewSlug(catalog[0]?.slug ?? "");
        }
        await refresh();
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Recovery service is not reachable.");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [refresh]);

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

  function resetRun() {
    runToken.current += 1;
    setRunningStep(null);
    setOutcomeNow(null);
    setWaitClock(null);
    setWaitPct(0);
    setTicker("Pick a case, then start recovery.");
  }

  async function startLiveProcess() {
    if (!detail) {
      return;
    }
    const token = ++runToken.current;
    setBusy("live");
    setError(null);
    setOutcomeNow(null);
    setWaitPct(0);
    setWaitClock(null);

    try {
      let current = detail;
      const hasProposal = current.audit?.some((line) => line.eventType === "AGENT_PROPOSE");
      if (!hasProposal) {
        setTicker("Reading the case before anything is charged.");
        const next = await proposeCase(current.caseId);
        setProposal(next);
        current = await recordAgentProposal(current.caseId, next);
        setDetail(current);
      }

      setTicker("This failure is now a recovery case.");
      await sleep(700);
      if (runToken.current !== token) {
        return;
      }

      const probability = current.score?.recoveryProbability ?? current.recoveryProbability;
      setTicker(
        probability == null
          ? "Following the usual plan for this failure."
          : `Chance they pay: ${Math.round(probability * 100)}%.`,
      );
      await sleep(900);
      if (runToken.current !== token) {
        return;
      }

      current = await getCase(current.caseId);
      setDetail(current);

      if (current.status === "RECOVERED") {
        setTicker("Already recovered.");
        setOutcomeNow({
          label: "Recovered",
          tone: "ok",
          detail: "This payment already came back. Nothing more to run.",
        });
        return;
      }

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
        setTicker(story.what);
        setOutcomeNow(null);

        const ok = await animateWait(story.waitMs, token);
        if (!ok) {
          return;
        }

        setTicker(`Running: ${actionLabel(next.actionType)}`);

        const doneBefore = completedSteps(current).size;
        try {
          current = await executeNext(current.caseId);
          if (current.policy?.verdict === "BLOCK") {
            setDetail(current);
            setRunningStep(null);
            setWaitClock(null);
            setTicker("Held for the other person.");
            setOutcomeNow({
              label: "Waiting for review",
              tone: "stop",
              detail: "This case waits for the other person. After they let it through, you can start again.",
            });
            return;
          }
        } catch (err) {
          const message = err instanceof Error ? err.message : "This step could not run.";
          setError(message);
          setTicker("Stopped.");
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
              label: "Stopped here",
              tone: "stop" as const,
              detail: "This step was skipped so the same window does not run again.",
            };
        setOutcomeNow(outcome);
        setRunningStep(null);
        setTicker(outcome.label);

        if (!progressed) {
          break;
        }

        await sleep(700);
      }

      if (runToken.current !== token) {
        return;
      }
      setWaitClock(null);
      setWaitPct(100);
      const recovered = current.status === "RECOVERED";
      setTicker(recovered ? "Recovered." : "The plan for this run is finished.");
      setOutcomeNow({
        label: recovered ? "Recovered" : "Plan finished",
        tone: recovered ? "ok" : "stop",
        detail: recovered
          ? "The money came back. This case is closed."
          : "Every planned step ran. The case may still be unpaid.",
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
  const held = detail?.policy?.verdict === "BLOCK" && detail.policy.reason !== "HUMAN_OVERRIDE";
  const canStart =
    detail != null &&
    busy === null &&
    detail.status !== "RECOVERED" &&
    !held &&
    (detail.playbook?.length ?? 0) > 0;
  const nextStep = detail ? remainingSteps(detail)[0] : null;
  const recommended = proposal ? actionLabel(proposal.recommendedAction) : nextStep ? actionLabel(nextStep.actionType) : "—";
  const atRisk = cases.reduce((sum, row) => sum + Number(row.amountAtRisk ?? 0), 0);
  const activity = detail
    ? [...detail.audit].reverse().slice(0, 6).map((line) => ({
        id: line.eventId,
        title: auditTitle(line.eventType),
        body: humanAuditBody(line),
      }))
    : [];

  return (
    <div className="wrap">
      {error ? <div className="err">{error}</div> : null}

      <div className="desk-shell">
        <aside className="desk-list">
          <div className="desk-list-head">
            <div>
              <p className="pill">Open cases</p>
              <strong>
                {cases.length} {cases.length === 1 ? "case" : "cases"}
              </strong>
            </div>
            <span className="muted">{inr(atRisk)} at risk</span>
          </div>

          {cases.length === 0 ? (
            <p className="empty">Nothing open. A failed payment will land here.</p>
          ) : (
            <>
              <div className="desk-cols">
                <span>Failure</span>
                <span>Status</span>
                <span>Amount</span>
              </div>
              <div className="rows">
                {cases.map((row) => (
                  <button
                    key={row.caseId}
                    type="button"
                    className={row.caseId === selectedId ? "desk-row active" : "desk-row"}
                    onClick={() =>
                      run("open", async () => {
                        resetRun();
                        const opened = await getCase(row.caseId);
                        setProposal(proposalFromAudit(opened));
                        setSelectedId(row.caseId);
                        setDetail(opened);
                      })
                    }
                  >
                    <span className="reason">{prettyWords(row.reason)}</span>
                    <span className="muted">{statusLabel(row.status)}</span>
                    <span className="desk-amt">{inr(row.amountAtRisk)}</span>
                  </button>
                ))}
              </div>
            </>
          )}

          {scenarios.length > 0 ? (
            <form
              className="desk-new"
              onSubmit={(event) => {
                event.preventDefault();
                if (!newSlug) {
                  return;
                }
                void run("new", async () => {
                  resetRun();
                  setProposal(null);
                  const created = await createIssue(newSlug);
                  await refresh(created.caseId);
                });
              }}
            >
              <label>
                <span className="pill">Open a failed payment</span>
                <select
                  value={newSlug}
                  disabled={busy !== null}
                  onChange={(event) => setNewSlug(event.target.value)}
                >
                  {scenarios.map((scenario) => (
                    <option key={scenario.slug} value={scenario.slug}>
                      {prettyWords(scenario.reason)}
                    </option>
                  ))}
                </select>
              </label>
              <button className="ghost-btn" type="submit" disabled={busy !== null || !newSlug}>
                {busy === "new" ? "Opening…" : "Open"}
              </button>
            </form>
          ) : null}
        </aside>

        <article className="desk-pack">
          {!detail ? (
            <p className="empty">Select a case to see what to do.</p>
          ) : (
            <>
              <header className="desk-pack-head">
                <div>
                  <p className="pill">This case</p>
                  <h2>{prettyWords(detail.reason)}</h2>
                  <p className="muted">{reasonBlurb(detail.reason)}</p>
                </div>
                <div className="desk-pack-actions">
                  <strong className="approval-amount">{inr(detail.amountAtRisk)}</strong>
                  <button
                    className={busy === "live" ? "start-btn running" : "start-btn"}
                    type="button"
                    disabled={!canStart}
                    onClick={() => void startLiveProcess()}
                  >
                    {busy === "live"
                      ? "Running…"
                      : detail.status === "RECOVERED"
                        ? "Recovered"
                        : held
                          ? "Waiting for review"
                          : "Start recovery"}
                  </button>
                </div>
              </header>

              <dl className="desk-facts">
                <div>
                  <dt>Status</dt>
                  <dd>{held ? "Waiting for review" : statusLabel(detail.status)}</dd>
                </div>
                <div>
                  <dt>Chance they pay</dt>
                  <dd>{chanceLabel(detail.score?.recoveryProbability ?? detail.recoveryProbability, detail.score?.status ?? detail.scoreStatus)}</dd>
                </div>
                <div>
                  <dt>Next step</dt>
                  <dd>{detail.status === "RECOVERED" ? "None" : recommended}</dd>
                </div>
                <div>
                  <dt>Progress</dt>
                  <dd>{total === 0 ? "No plan yet" : `${doneCount} of ${total} steps`}</dd>
                </div>
              </dl>

              {detail.policy ? (
                <p className={`desk-note ${held ? "hold" : ""}`}>{policyBanner(detail.policy.reason, detail.policy.verdict)}</p>
              ) : null}

              {proposal && !held && detail.status !== "RECOVERED" ? (
                <p className="desk-note">{proposal.reasoning}</p>
              ) : null}

              {busy === "live" || waitClock || outcomeNow ? (
                <section className="desk-now">
                  <div>
                    <p className="pill">Now</p>
                    <strong>{ticker}</strong>
                    {outcomeNow ? <p className={`outcome-line ${outcomeNow.tone}`}>{outcomeNow.detail}</p> : null}
                  </div>
                  <div className="meter">
                    <span style={{ width: `${waitClock ? waitPct : meterPct}%` }} />
                  </div>
                </section>
              ) : null}

              <section className="desk-plan">
                <p className="pill">Plan</p>
                {(detail.playbook ?? []).length === 0 ? (
                  <p className="muted">No recovery plan for this failure yet.</p>
                ) : (
                  <ol>
                    {(detail.playbook ?? []).map((step) => {
                      const state = stepState(detail, step.step, runningStep);
                      const finished = latestActionForStep(detail, step.step);
                      return (
                        <li key={step.step} className={state}>
                          <span className="n">{step.step}</span>
                          <div>
                            <strong>{actionLabel(step.actionType)}</strong>
                            <span className="muted">{step.note}</span>
                          </div>
                          <span className="badge">
                            {state === "running" ? "Now" : state === "done" ? stepResult(finished?.status ?? "EXECUTED") : "Next"}
                          </span>
                        </li>
                      );
                    })}
                  </ol>
                )}
              </section>

              <section className="desk-activity">
                <p className="pill">What happened</p>
                {activity.length === 0 ? (
                  <span className="muted">Nothing yet. Start recovery to run the plan.</span>
                ) : (
                  activity.map((line) => (
                    <div key={line.id} className="desk-activity-line">
                      <strong>{line.title}</strong>
                      {line.body ? <span className="muted">{line.body}</span> : null}
                    </div>
                  ))
                )}
              </section>
            </>
          )}
        </article>
      </div>
    </div>
  );
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

function humanAuditBody(line: { eventType: string; details: Record<string, unknown> | null }) {
  const details = line.details;
  if (!details) {
    return "";
  }
  if (typeof details.note === "string" && details.note.trim()) {
    return details.note;
  }
  if (typeof details.reasoning === "string" && details.reasoning.trim()) {
    return details.reasoning;
  }
  if (typeof details.recommendedAction === "string") {
    return actionLabel(details.recommendedAction);
  }
  if (typeof details.reason === "string") {
    return prettyWords(details.reason);
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
