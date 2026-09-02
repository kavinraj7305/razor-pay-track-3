"use client";

import { useCallback, useEffect, useState } from "react";
import {
  createAllIssues,
  createIssue,
  executeNext,
  getCase,
  inr,
  listCases,
  listScenarios,
  pct,
} from "@/lib/api";
import type { CaseDetail, CaseSummary, Scenario } from "@/lib/types";

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

export default function RecoveryDesk() {
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [cases, setCases] = useState<CaseSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<CaseDetail | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

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
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Backend is not reachable on :8080");
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

  return (
    <div className="desk">
      <header className="desk-bar">
        <div>
          <p className="pill">Day 3 desk · before the agent</p>
          <h1>Recovery issues</h1>
          <p>
            Create a failure, see the four-step playbook Java will run, and read P(recovery) from the model.
            The agent is not in this loop yet.
          </p>
        </div>
        <button
          className="primary-btn"
          disabled={busy !== null}
          onClick={() =>
            run("all", async () => {
              await createAllIssues();
              await refresh(selectedId);
            })
          }
        >
          {busy === "all" ? "Creating pack…" : "Create all 8 issues"}
        </button>
      </header>

      <div className="wrap">
        {error ? <div className="err">{error}</div> : null}

        <section>
          <p className="muted" style={{ marginBottom: "0.5rem" }}>
            One click opens a RecoveryCase (same simulate APIs as Postman).
          </p>
          <div className="create-grid">
            {scenarios.map((scenario) => (
              <button
                key={scenario.slug}
                className="issue-btn"
                disabled={busy !== null}
                onClick={() =>
                  run(scenario.slug, async () => {
                    const created = await createIssue(scenario.slug);
                    await refresh(created.caseId);
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
              <p className="empty">No demo cases yet. Create one above. Training rows stay hidden.</p>
            ) : (
              <div className="rows">
                {cases.map((row) => (
                  <button
                    key={row.caseId}
                    className={row.caseId === selectedId ? "case-row active" : "case-row"}
                    onClick={() =>
                      run("open", async () => {
                        setSelectedId(row.caseId);
                        setDetail(await getCase(row.caseId));
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
              <h2>This case</h2>
              {detail ? (
                <button
                  className="ghost-btn"
                  disabled={busy !== null || detail.status === "RECOVERED"}
                  onClick={() =>
                    run("exec", async () => {
                      const next = await executeNext(detail.caseId);
                      setDetail(next);
                      await refresh(next.caseId);
                    })
                  }
                >
                  {busy === "exec" ? "Running…" : "Run next playbook step"}
                </button>
              ) : null}
            </div>
            {!detail ? (
              <p className="empty">Select a case to see playbook + score.</p>
            ) : (
              <div className="detail">
                <div className="score-card">
                  <div className="big">{pct(detail.score?.recoveryProbability ?? detail.recoveryProbability)}</div>
                  <div>
                    <strong>{detail.reason}</strong>
                    <p className="muted" style={{ color: "#c9d4e3" }}>
                      {detail.score?.status === "SCORED"
                        ? `P(recovery) for this customer. ${detail.score.skipRetry ? "Low P may skip extra retry." : "Playbook still owns execute."}`
                        : detail.score?.status === "LOW_DATA"
                          ? `Not enough labelled outcomes (${detail.score.labelledOutcomes} / ${detail.score.minLabelledOutcomes}). Playbook only.`
                          : "ML is down or unreachable. Playbook only."}
                    </p>
                    <p className="muted" style={{ color: "#c9d4e3" }}>
                      {inr(detail.amountAtRisk)} · {detail.caseId}
                    </p>
                  </div>
                </div>

                <div className="playbook">
                  <h3 className="display" style={{ margin: "0 0 0.6rem", fontSize: "1.05rem" }}>
                    Playbook Java will run
                  </h3>
                  <ol>
                    {(detail.playbook ?? []).map((step) => (
                      <li key={step.step}>
                        <span className="n">{step.step}</span>
                        <div>
                          <strong>{step.actionType.split("_").join(" ")}</strong>
                          <span className="muted">{step.note}</span>
                        </div>
                      </li>
                    ))}
                  </ol>
                </div>

                <div className="audit">
                  <strong>Audit so far</strong>
                  {(detail.audit ?? []).length === 0 ? (
                    <span className="muted">No audit rows yet.</span>
                  ) : (
                    detail.audit.map((line) => (
                      <div key={line.eventId}>
                        {line.eventType} · {line.action}
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
