"use client";

import { useCallback, useEffect, useState } from "react";
import { DeskChrome, RoleGate } from "@/components/DeskChrome";
import { approveCase, inr, pendingApprovals, prettyError, rejectCase } from "@/lib/api";
import type { ApprovalItem } from "@/lib/types";

export default function ApprovalsPage() {
  return (
    <RoleGate allow={["APPROVER", "ADMIN"]}>
      <DeskChrome
        kicker="Human in the loop · policy guard"
        title="Approval queue"
        blurb="These cases are in the middle — PolicyEngine blocked execute. Approve so the desk can continue, or reject and keep the money untouched."
      >
        <QueueBody />
      </DeskChrome>
    </RoleGate>
  );
}

function QueueBody() {
  const [rows, setRows] = useState<ApprovalItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [notes, setNotes] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    setRows(await pendingApprovals());
  }, []);

  useEffect(() => {
    void load().catch((err) => setError(prettyError(err)));
  }, [load]);

  async function decide(caseId: string, kind: "approve" | "reject") {
    setBusy(`${kind}-${caseId}`);
    setError(null);
    try {
      const note = notes[caseId] ?? "";
      if (kind === "approve") {
        await approveCase(caseId, note);
      } else {
        await rejectCase(caseId, note);
      }
      await load();
    } catch (err) {
      setError(prettyError(err));
    } finally {
      setBusy(null);
    }
  }

  const blocked = rows.reduce((sum, row) => sum + Number(row.amountAtRisk ?? 0), 0);

  return (
    <div className="wrap">
      {error ? <div className="err">{error}</div> : null}
      {rows.length === 0 ? (
        <section className="panel empty-queue">
          <p className="pill">Queue clear</p>
          <h2>Nothing waiting on policy</h2>
          <p className="muted">
            Ask the operator to start a risk or high-amount case — those land here for a human
            decision.
          </p>
        </section>
      ) : (
        <>
          <section className="queue-strip">
            <div>
              <p className="pill">Waiting on you</p>
              <strong>
                {rows.length} {rows.length === 1 ? "case" : "cases"}
              </strong>
            </div>
            <span className="muted">{inr(blocked)} still blocked by PolicyEngine</span>
          </section>
          <div className="approval-list">
            {rows.map((row) => {
              const facts = parseFacts(row.agentReasoning);
              return (
                <article key={row.caseId} className="approval-row">
                  <div className="approval-main">
                    <div className="approval-title">
                      <h2>{prettyWords(row.reason)}</h2>
                      <span className="badge block">{policyLabel(row.policyReason)}</span>
                      {row.escalate ? <span className="chip warn">Escalate</span> : null}
                    </div>
                    <div className="approval-meta">
                      <strong className="approval-amount">{inr(row.amountAtRisk)}</strong>
                      <span className="chip">{row.status}</span>
                      <code className="approval-id" title={row.caseId}>
                        {shortId(row.caseId)}
                      </code>
                    </div>
                    <p className="muted">
                      Recommended {prettyWords(row.recommendedAction) || "review"} · Agent{" "}
                      {prettyWords(row.agentDiagnosis) || "no proposal yet"}
                    </p>
                    {facts ? (
                      <div className="approval-facts">
                        <p className="pill">{facts.title}</p>
                        <div className="approval-chips">
                          {facts.pairs.map((fact) => (
                            <span key={`${row.caseId}-${fact.key}`} className="chip">
                              {fact.key} {prettyWords(fact.value)}
                            </span>
                          ))}
                        </div>
                      </div>
                    ) : row.agentReasoning ? (
                      <p className="approval-note-text">{row.agentReasoning}</p>
                    ) : null}
                  </div>
                  <div className="approval-decide">
                    <label>
                      Note for audit
                      <textarea
                        value={notes[row.caseId] ?? ""}
                        onChange={(event) =>
                          setNotes((prev) => ({ ...prev, [row.caseId]: event.target.value }))
                        }
                        placeholder="Why you are approving or holding this"
                        rows={3}
                      />
                    </label>
                    <div className="approval-actions">
                      <button
                        className="ghost-btn ask-btn"
                        type="button"
                        disabled={busy !== null}
                        onClick={() => void decide(row.caseId, "reject")}
                      >
                        {busy === `reject-${row.caseId}` ? "Holding…" : "Reject / hold"}
                      </button>
                      <button
                        className="start-btn"
                        type="button"
                        disabled={busy !== null}
                        onClick={() => void decide(row.caseId, "approve")}
                      >
                        {busy === `approve-${row.caseId}` ? "Approving…" : "Approve execute"}
                      </button>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}

function prettyWords(value: string | null | undefined) {
  return (value ?? "").replaceAll("_", " ").replaceAll("-", " ").trim();
}

function policyLabel(reason: string) {
  if (reason === "RISK_OR_CANCELLED") {
    return "Risk hold";
  }
  if (reason === "AGENT_ESCALATE") {
    return "Agent escalate";
  }
  if (reason === "HUMAN_APPROVAL_AMOUNT") {
    return "High amount";
  }
  return prettyWords(reason) || "Needs review";
}

function shortId(caseId: string) {
  return caseId.length > 22 ? `${caseId.slice(0, 12)}…${caseId.slice(-4)}` : caseId;
}

function parseFacts(text: string) {
  if (!text) {
    return null;
  }
  const pairs = [...text.matchAll(/([A-Za-z_][\w]*)=([^,]+)/g)].map((match) => ({
    key: prettyWords(match[1]),
    value: match[2].trim(),
  }));
  if (pairs.length === 0) {
    return null;
  }
  const title = text.split(/[:]/)[0]?.trim();
  return {
    title: title && title.length < 36 && !title.includes("=") ? title : "Fallback rules",
    pairs,
  };
}
