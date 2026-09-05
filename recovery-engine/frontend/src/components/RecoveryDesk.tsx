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
  chanceLabel,
  explainStep,
  howThisRuns,
  pickRecoveryChance,
  prettyWords,
  reasonBlurb,
  scheduleWhen,
  statusLabel,
  stepTitle,
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
  const [ticker, setTicker] = useState("");
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
    setTicker("");
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
        setTicker("Reading the case.");
        const next = await proposeCase(current.caseId);
        setProposal(next);
        current = await recordAgentProposal(current.caseId, next);
        setDetail(current);
      }

      setTicker("Opening recovery.");
      await sleep(600);
      if (runToken.current !== token) {
        return;
      }

      const probability = current.score?.recoveryProbability ?? current.recoveryProbability;
      setTicker(
        probability == null
          ? "Following the usual plan for this failure."
          : `This customer is ${Math.round(probability * 100)}% likely to pay.`,
      );
      await sleep(800);
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
          detail: "This payment already came back.",
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
        setTicker(actionLabel(next.actionType));
        setOutcomeNow(null);

        const ok = await animateWait(story.waitMs, token);
        if (!ok) {
          return;
        }

        const doneBefore = completedSteps(current).size;
        try {
          current = await executeNext(current.caseId);
          if (current.policy?.verdict === "BLOCK") {
            setDetail(current);
            setRunningStep(null);
            setWaitClock(null);
            setTicker("Waiting for the other person.");
            setOutcomeNow({
              label: "Waiting for review",
              tone: "stop",
              detail: "Nothing is charged until they let this through.",
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
              detail: "This step was skipped so we do not repeat it.",
            };
        setOutcomeNow(outcome);
        setRunningStep(null);
        setTicker(outcome.label);

        if (!progressed) {
          break;
        }

        await sleep(600);
      }

      if (runToken.current !== token) {
        return;
      }
      setWaitClock(null);
      setWaitPct(100);
      const recovered = current.status === "RECOVERED";
      setTicker(recovered ? "Recovered." : "");
      setOutcomeNow({
        label: recovered ? "Recovered" : "Finished",
        tone: recovered ? "ok" : "stop",
        detail: recovered ? "The money came back." : "",
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
  const finished = total > 0 && doneCount >= total;
  const held = detail?.policy?.verdict === "BLOCK" && detail.policy.reason !== "HUMAN_OVERRIDE";
  const running = busy === "live";
  const canStart =
    detail != null &&
    busy === null &&
    detail.status !== "RECOVERED" &&
    !held &&
    !finished &&
    (detail.playbook?.length ?? 0) > 0;
  const atRisk = cases.reduce((sum, row) => sum + Number(row.amountAtRisk ?? 0), 0);
  const recoveryChance = detail
    ? pickRecoveryChance({
        proposalScore: proposal?.mlScore,
        caseScore: detail.score?.recoveryProbability ?? detail.recoveryProbability,
        actionNotes: (detail.actions ?? []).map((action) => action.note),
        audit: detail.audit,
      })
    : null;
  const chance = chanceLabel(recoveryChance);
  const next = detail ? nextUseful(detail, proposal) : null;
  const story = detail
    ? caseStory(detail, { held, finished, running, ticker, next, proposal, recoveryChance })
    : "";

  return (
    <div className="wrap desk-page">
      {error ? <p className="err">{error}</p> : null}

      <aside className="desk-side">
        <p className="desk-open-label">1 · Pick or simulate</p>
        <h2>Practice cases</h2>
        <p className="desk-lede">
          {cases.length === 0
            ? "Nothing open. Pick a failure and simulate it — a practice case appears on the right."
            : `${cases.length} open · ${inr(atRisk)} at risk. Click one to read its plan.`}
        </p>
        {cases.length > 0 ? (
          <ul className="desk-cases">
            {cases.map((row) => (
              <li key={row.caseId}>
                <button
                  type="button"
                  className={row.caseId === selectedId ? "desk-pick on" : "desk-pick"}
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
                  <span>{prettyWords(row.reason)}</span>
                  <span>{inr(row.amountAtRisk)}</span>
                </button>
              </li>
            ))}
          </ul>
        ) : null}

        {scenarios.length > 0 ? (
          <form
            className="desk-open"
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
            <label className="desk-open-label" htmlFor="desk-failure">
              What failed
            </label>
            <select
              id="desk-failure"
              value={newSlug}
              disabled={busy !== null}
              aria-label="Failure type"
              onChange={(event) => setNewSlug(event.target.value)}
            >
              {scenarios.map((scenario) => (
                <option key={scenario.slug} value={scenario.slug}>
                  {prettyWords(scenario.reason)}
                </option>
              ))}
            </select>
            <button className="ghost-btn" type="submit" disabled={busy !== null || !newSlug}>
              {busy === "new" ? "Opening…" : "Simulate"}
            </button>
            <p className="desk-hint">Creates a practice case. It does not charge anyone.</p>
          </form>
        ) : null}
      </aside>

      <section className="desk-case">
        <p className="desk-open-label">2 · This case</p>
        {!detail ? (
          <div className="desk-teach">
            <h2>How to read this page</h2>
            <p>1. Simulate a failed payment on the left. A case appears.</p>
            <p>2. Press Start. The first required step always runs.</p>
            <p>3. After that one try, if chance they pay is low, extra silent retries are skipped.</p>
            <p>4. The last channel is usually a payment link — not another charge on the same card.</p>
          </div>
        ) : (
          <>
            <header className="desk-hero">
              <div>
                <h2>{prettyWords(detail.reason)}</h2>
                <p>{reasonBlurb(detail.reason)}</p>
                <p className="desk-teach-line">{howThisRuns(detail.reason)}</p>
                <p className="desk-meta">
                  <strong>{inr(detail.amountAtRisk)}</strong>
                  <span>{held ? "Waiting for review" : finished ? "Finished" : statusLabel(detail.status)}</span>
                  {chance ? <span>{chance}</span> : null}
                </p>
              </div>
              <div className="desk-start">
                <button
                  className={running ? "start-btn running" : "start-btn"}
                  type="button"
                  disabled={!canStart}
                  onClick={() => void startLiveProcess()}
                >
                  {running
                    ? "Running…"
                    : detail.status === "RECOVERED"
                      ? "Recovered"
                      : held
                        ? "Waiting"
                        : finished
                          ? "Finished"
                          : "Start recovery"}
                </button>
                {canStart ? (
                  <p className="desk-hint">Runs the first required step, then decides extras.</p>
                ) : null}
              </div>
            </header>

            <p className="desk-story">{story}</p>

            {running ? (
              <div className="desk-live" aria-live="polite">
                <span>{ticker || "Working…"}</span>
                <span className="meter">
                  <span style={{ width: `${waitClock ? waitPct : Math.round((doneCount / Math.max(total, 1)) * 100)}%` }} />
                </span>
              </div>
            ) : null}

            {(detail.playbook ?? []).length > 0 ? (
              <>
              <p className="desk-steps-label">The plan — when it would run, then what actually happened</p>
              <ol className="desk-steps">
                {(detail.playbook ?? []).map((step) => {
                  const state = stepState(detail, step.step, runningStep);
                  const done = latestActionForStep(detail, step.step);
                  const when = scheduleWhen(done?.when ?? step.when, done?.waitHours ?? step.waitHours);
                  const explained = explainStep({
                    failureReason: detail.reason,
                    actionType: step.actionType,
                    step: step.step,
                    status: state === "running" ? null : done?.status ?? null,
                    actionNote: done?.note ?? null,
                    playbookNote: step.note,
                    policyVerdict: detail.policy?.verdict ?? null,
                    policyReason: detail.policy?.reason ?? null,
                    recommendedAction: proposal?.recommendedAction ?? detail.policy?.recommendedAction ?? null,
                    scoreStatus: detail.score?.status ?? detail.scoreStatus,
                    mlScore: recoveryChance,
                  });
                  return (
                    <li key={step.step} className={state}>
                      <div className="desk-step-top">
                        <strong>
                          {step.step}. {stepTitle(step.step, step.actionType)}
                        </strong>
                        <em>{state === "running" ? "Now" : explained.result}</em>
                      </div>
                      {when ? <p className="desk-step-when">{when}</p> : null}
                      <p>{explained.what}</p>
                      {explained.why ? <p className="desk-step-why">{explained.why}</p> : null}
                    </li>
                  );
                })}
              </ol>
              </>
            ) : (
              <p className="desk-lede">No plan for this failure yet.</p>
            )}

            {outcomeNow && !running && outcomeNow.detail ? (
              <p className={`desk-end ${outcomeNow.tone}`}>{outcomeNow.detail}</p>
            ) : null}
          </>
        )}
      </section>
    </div>
  );
}

function nextUseful(detail: CaseDetail, proposal: CaseProposal | null) {
  if (detail.status === "RECOVERED") {
    return null;
  }
  const skipRetries =
    proposal?.recommendedAction === "SKIP_EXTRA_RETRY" || detail.policy?.verdict === "SKIP_RETRY";
  const leftover = remainingSteps(detail);
  const pick =
    leftover.find((step) => !skipRetries || !step.actionType.includes("RETRY")) ?? leftover[0] ?? null;
  return pick ? actionLabel(pick.actionType) : null;
}

function caseStory(
  detail: CaseDetail,
  state: {
    held: boolean;
    finished: boolean;
    running: boolean;
    ticker: string;
    next: string | null;
    proposal: CaseProposal | null;
    recoveryChance: number | null;
  },
) {
  if (state.running) {
    return state.ticker || "Recovery is running.";
  }
  if (detail.status === "RECOVERED") {
    return "This payment came back. The case is closed.";
  }
  if (state.held) {
    return "Held for the other person. Nothing is charged until they let it through.";
  }
  const skipped = (detail.actions ?? []).some(
    (action) => action.status === "CANCELLED" && action.actionType.includes("RETRY"),
  );
  const linkSent = (detail.actions ?? []).some(
    (action) => action.actionType === "SEND_PAYMENT_LINK" && action.status === "EXECUTED",
  );
  if (linkSent && skipped) {
    const p =
      state.recoveryChance != null ? `P(recovery) was ${Math.round(state.recoveryChance * 100)}%` : "P(recovery) was too low";
    return `We ran the first payday retry once. After that, ${p}, so extra silent retries were skipped and we sent a payment link.`;
  }
  if (linkSent) {
    return "We sent one payment link so they can pay with another method.";
  }
  if (state.finished) {
    return "Every step that should run has run. The case may still be unpaid.";
  }
  if (skipped && state.next) {
    return `Silent retries on the same card were skipped. Next we ${state.next.toLowerCase()}.`;
  }
  if (state.next) {
    return `Next we ${state.next.toLowerCase()}.`;
  }
  return "Start recovery to run this plan.";
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
    mlScore: numberOrNull(raw.mlScore ?? raw.recoveryProbability),
    escalate: Boolean(raw.escalate),
    actionsAvailable: Array.isArray(raw.actionsAvailable) ? raw.actionsAvailable.map(String) : ["propose"],
    executes: false,
    model: String(raw.model ?? "fallback-rules"),
    fallbackUsed: Boolean(raw.fallbackUsed),
  };
}

function numberOrNull(value: unknown): number | null {
  if (value == null || value === "") {
    return null;
  }
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
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
