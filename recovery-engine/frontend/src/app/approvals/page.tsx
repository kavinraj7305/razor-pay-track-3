"use client";

import { useCallback, useEffect, useState } from "react";
import { DeskChrome, RoleGate } from "@/components/DeskChrome";
import { approveCase, inr, pendingApprovals, prettyError, proposeCase, rejectCase } from "@/lib/api";
import type { ApprovalItem, CaseProposal } from "@/lib/types";

const PROCESS = [
  { n: "01", title: "Detected", copy: "Failed payment became a case." },
  { n: "02", title: "Playbook", copy: "Java planned the reason folder." },
  { n: "03", title: "Agent", copy: "Proposed only. Cannot charge." },
  { n: "04", title: "Policy hold", copy: "Java stopped before a retry." },
  { n: "05", title: "You decide", copy: "Hold it, or let the CEO continue." },
];

export default function ApprovalsPage() {
  return (
    <RoleGate allow={["APPROVER"]}>
      <DeskChrome
        kicker="Human in the loop"
        title="Policy queue"
        blurb="Java already stopped these. Read the case and the agent note, write why, then hold or let through. You do not charge."
      >
        <QueueBody />
      </DeskChrome>
    </RoleGate>
  );
}

function QueueBody() {
  const [rows, setRows] = useState<ApprovalItem[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [coach, setCoach] = useState<CoachBrief | null>(null);
  const [asking, setAsking] = useState(false);

  const load = useCallback(async () => {
    const next = await pendingApprovals();
    setRows(next);
    setSelectedId((current) => {
      if (current && next.some((row) => row.caseId === current)) {
        return current;
      }
      return next[0]?.caseId ?? null;
    });
    setCoach(null);
  }, []);

  useEffect(() => {
    void load().catch((err) => setError(prettyError(err)));
  }, [load]);

  async function decide(caseId: string, kind: "approve" | "reject") {
    const note = (notes[caseId] ?? "").trim();
    if (!note) {
      setError("Write why you are holding or letting this through.");
      return;
    }
    setBusy(`${kind}-${caseId}`);
    setError(null);
    try {
      if (kind === "approve") {
        await approveCase(caseId, note);
      } else {
        await rejectCase(caseId, note);
      }
      setNotes((prev) => {
        const next = { ...prev };
        delete next[caseId];
        return next;
      });
      await load();
    } catch (err) {
      setError(prettyError(err));
    } finally {
      setBusy(null);
    }
  }

  async function askWhatToDo(row: ApprovalItem | null) {
    setAsking(true);
    setError(null);
    let live: CaseProposal | null = null;
    if (row) {
      try {
        live = await proposeCase(row.caseId);
      } catch {
        live = null;
      }
    }
    setCoach(buildCoach(row, live));
    setAsking(false);
  }

  const blocked = rows.reduce((sum, row) => sum + Number(row.amountAtRisk ?? 0), 0);
  const selected = rows.find((row) => row.caseId === selectedId) ?? null;
  const note = selected ? (notes[selected.caseId] ?? "").trim() : "";

  return (
    <div className="wrap">
      <ol className="process-rail">
        {PROCESS.map((step, index) => (
          <li key={step.n} className={index === 4 ? "process-step now" : "process-step"}>
            <span className="process-n">{step.n}</span>
            <strong>{step.title}</strong>
            <p>{step.copy}</p>
          </li>
        ))}
      </ol>

      {error ? <div className="err">{error}</div> : null}
      {coach && !selected && rows.length === 0 ? <CoachPanel brief={coach} /> : null}

      {rows.length === 0 ? (
        <section className="panel empty-queue">
          <p className="pill">Queue clear</p>
          <h2>Nothing waiting on you</h2>
          <p className="muted">
            Risk, agent escalate, and amounts at or above ₹80,000 wait here. After you let one
            through, the CEO presses Start. You never run the charge.
          </p>
          <button className="ghost-btn ask-btn coach-ask" type="button" disabled={asking} onClick={() => void askWhatToDo(null)}>
            {asking ? "Asking…" : "Ask what to do"}
          </button>
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
            <span className="muted">{inr(blocked)} held before any retry</span>
          </section>

          <div className="approval-shell">
            <aside className="queue-side">
              <p className="pill">Queue</p>
              {rows.map((row) => (
                <button
                  key={row.caseId}
                  type="button"
                  className={row.caseId === selected?.caseId ? "queue-pick active" : "queue-pick"}
                  onClick={() => {
                    setSelectedId(row.caseId);
                    setCoach(null);
                  }}
                >
                  <span className="queue-pick-top">
                    <strong>{prettyWords(row.reason)}</strong>
                    <span>{inr(row.amountAtRisk)}</span>
                  </span>
                  <span className="muted">{policyLabel(row.policyReason)}</span>
                </button>
              ))}
            </aside>

            {selected ? (
              <article className="review-pack">
                <header className="review-head">
                  <div>
                    <p className="pill">Case under review</p>
                    <h2>{prettyWords(selected.reason)}</h2>
                  </div>
                  <div className="review-head-actions">
                    <strong className="approval-amount">{inr(selected.amountAtRisk)}</strong>
                    <button
                      className="ghost-btn ask-btn coach-ask"
                      type="button"
                      disabled={asking || busy !== null}
                      onClick={() => void askWhatToDo(selected)}
                    >
                      {asking ? "Asking…" : "Ask what to do"}
                    </button>
                  </div>
                </header>

                {coach && coach.caseId === selected.caseId ? <CoachPanel brief={coach} /> : null}

                <section className="review-step">
                  <span className="review-n">1</span>
                  <div>
                    <p className="pill">The case</p>
                    <h3>What failed</h3>
                    <dl className="fact-grid">
                      <Fact label="Amount at risk" value={inr(selected.amountAtRisk)} />
                      <Fact label="Priority" value={prettyWords(selected.priority) || "—"} />
                      <Fact label="Source" value={prettyWords(selected.source) || "—"} />
                      <Fact label="Source id" value={selected.sourceId || "—"} mono />
                      <Fact label="Customer" value={selected.customerId || "Not attached"} mono />
                      <Fact label="Merchant" value={selected.merchantId || "—"} mono />
                      <Fact label="Status" value={prettyWords(selected.status)} />
                      <Fact label="Case id" value={shortId(selected.caseId)} mono />
                    </dl>
                  </div>
                </section>

                <section className="review-step">
                  <span className="review-n">2</span>
                  <div>
                    <p className="pill">Policy</p>
                    <h3>Why Java stopped it</h3>
                    <p className="review-copy">{policyExplain(selected.policyReason)}</p>
                    <div className="approval-chips">
                      <span className="badge block">{policyLabel(selected.policyReason)}</span>
                      {selected.escalate ? <span className="chip warn">Escalate</span> : null}
                      <span className="chip">Playbook {prettyWords(selected.playbookAction) || "review"}</span>
                    </div>
                  </div>
                </section>

                <section className="review-step">
                  <span className="review-n">3</span>
                  <div>
                    <p className="pill">Agent</p>
                    <h3>Proposal — cannot charge</h3>
                    {hasAgent(selected) ? (
                      <div className="agent-box">
                        <dl className="fact-grid">
                          <Fact
                            label="Diagnosis"
                            value={prettyWords(selected.agentDiagnosis) || "No label"}
                          />
                          <Fact
                            label="Recommends"
                            value={prettyWords(selected.recommendedAction) || "Review"}
                          />
                          <Fact
                            label="Playbook default"
                            value={prettyWords(selected.playbookAction) || "—"}
                          />
                          <Fact
                            label="Deviates"
                            value={selected.deviatesFromPlaybook ? "Yes — different from playbook" : "No — agrees"}
                          />
                          <Fact label="This customer P(recovery)" value={scoreLabel(selected.mlScore)} />
                          <Fact label="Confidence" value={scoreLabel(selected.confidence)} />
                          <Fact
                            label="Model"
                            value={
                              selected.fallbackUsed
                                ? `Fallback rules${selected.agentModel ? ` · ${selected.agentModel}` : ""}`
                                : selected.agentModel || "Agent"
                            }
                          />
                          <Fact label="Can execute" value="No. Propose only." />
                        </dl>
                        {selected.agentReasoning ? (
                          <p className="agent-why">{cleanReason(selected.agentReasoning)}</p>
                        ) : (
                          <p className="muted">No written reasoning on the proposal.</p>
                        )}
                      </div>
                    ) : (
                      <p className="review-copy">
                        No agent proposal on this case yet. Policy still held it on the reason or
                        the amount. Your sign-off is what matters.
                      </p>
                    )}
                  </div>
                </section>

                <section className="review-step decide">
                  <span className="review-n">4</span>
                  <div>
                    <p className="pill">Your decision</p>
                    <h3>Sign the book</h3>
                    <p className="review-copy">
                      Hold keeps the block. Let through writes a human override. The CEO then
                      presses Start. You do not run the playbook from here.
                    </p>
                    <label>
                      Note for audit
                      <textarea
                        value={notes[selected.caseId] ?? ""}
                        onChange={(event) =>
                          setNotes((prev) => ({ ...prev, [selected.caseId]: event.target.value }))
                        }
                        placeholder="Why you are holding or letting this through"
                        rows={4}
                      />
                    </label>
                    <div className="approval-actions">
                      <button
                        className="ghost-btn ask-btn"
                        type="button"
                        disabled={busy !== null || !note}
                        onClick={() => void decide(selected.caseId, "reject")}
                      >
                        {busy === `reject-${selected.caseId}` ? "Holding…" : "Hold it"}
                      </button>
                      <button
                        className="start-btn"
                        type="button"
                        disabled={busy !== null || !note}
                        onClick={() => void decide(selected.caseId, "approve")}
                      >
                        {busy === `approve-${selected.caseId}` ? "Sending…" : "Let it through"}
                      </button>
                    </div>
                    {coach && coach.caseId === selected.caseId && coach.suggestedNote ? (
                      <button
                        className="ghost-btn note-fill"
                        type="button"
                        onClick={() =>
                          setNotes((prev) => ({ ...prev, [selected.caseId]: coach.suggestedNote }))
                        }
                      >
                        Use the agent note
                      </button>
                    ) : null}
                    {!note ? <p className="muted">A written note is required before either button works.</p> : null}
                  </div>
                </section>
              </article>
            ) : null}
          </div>
        </>
      )}
    </div>
  );
}

type CoachBrief = {
  caseId: string | null;
  doThis: string;
  lines: string[];
  suggestedNote: string;
  liveAgent: boolean;
};

function CoachPanel({ brief }: { brief: CoachBrief }) {
  return (
    <aside className="coach-box">
      <p className="pill">{brief.liveAgent ? "Agent on this case" : "Agent brief"}</p>
      <strong>{brief.doThis}</strong>
      <ol>
        {brief.lines.map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ol>
      <p className="muted">The agent still cannot charge. You only sign the hold or the let-through.</p>
    </aside>
  );
}

function buildCoach(row: ApprovalItem | null, live: CaseProposal | null): CoachBrief {
  if (!row) {
    return {
      caseId: null,
      doThis: "Nothing is waiting. You can leave this queue.",
      liveAgent: false,
      suggestedNote: "",
      lines: [
        "Cases land here only after Java blocks them — risk, agent escalate, or ₹80,000 and above.",
        "When one appears: read the case, ask what to do, write a note, then Hold it or Let it through.",
        "After Let it through, the CEO presses Start. You never run the playbook.",
      ],
    };
  }

  const reason = prettyWords(row.reason) || "this failure";
  const amount = inr(row.amountAtRisk);
  const recommend = prettyWords(live?.recommendedAction || row.recommendedAction) || "review";
  const playbook = prettyWords(live?.defaultPlaybookAction || row.playbookAction) || "the playbook";
  const diagnosis = prettyWords(live?.diagnosis || row.agentDiagnosis);
  const score = scoreLabel(live?.mlScore ?? row.mlScore);
  const escalate = Boolean(live?.escalate || row.escalate);
  const risk = row.policyReason === "RISK_OR_CANCELLED";
  const high = row.policyReason === "HUMAN_APPROVAL_AMOUNT";

  let doThis = `Hold ${reason} unless you have a written reason to let the CEO continue.`;
  let suggestedNote = `Holding ${reason} at ${amount}. ${policyLabel(row.policyReason)}. No auto-charge.`;
  if (risk) {
    doThis = `Hold this. Risk or a cancelled payment — do not let a retry through unless you are sure.`;
    suggestedNote = `Hold. ${reason} is a risk or cancel. Java must not auto-charge.`;
  } else if (high && !escalate) {
    doThis = `This is a high-amount hold. Let it through only if chasing ${amount} is justified.`;
    suggestedNote = `High amount ${amount} on ${reason}. Letting through so the CEO can Start.`;
  } else if (escalate) {
    doThis = `The agent wants a person. Hold unless you accept their recommend: ${recommend}.`;
    suggestedNote = `Agent escalate on ${reason}. Recommended ${recommend}. Holding until reviewed.`;
  }

  const lines = [
    `${amount} is sitting on ${reason}. Policy stopped it: ${policyExplain(row.policyReason)}`,
    diagnosis
      ? `Agent diagnosis: ${diagnosis}. It recommends ${recommend}. Playbook default is ${playbook}.`
      : `No stored diagnosis yet. Policy still held it. Treat recommend as ${recommend}.`,
    `This customer P(recovery) is ${score}. That is odds they pay — not a licence to charge.`,
    `Your move: write a note, then Hold it (block stays) or Let it through (CEO can Start). You do not press Start.`,
  ];

  return {
    caseId: row.caseId,
    doThis,
    lines,
    suggestedNote,
    liveAgent: Boolean(live),
  };
}

function Fact({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd className={mono ? "mono" : undefined}>{value}</dd>
    </div>
  );
}

function hasAgent(row: ApprovalItem) {
  return Boolean(row.agentDiagnosis || row.agentReasoning);
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

function policyExplain(reason: string) {
  if (reason === "RISK_OR_CANCELLED") {
    return "Risk or a cancelled payment. Java will not auto-charge. A person has to let it through.";
  }
  if (reason === "AGENT_ESCALATE") {
    return "The agent said escalate or do not retry. Policy blocked the step until you sign.";
  }
  if (reason === "HUMAN_APPROVAL_AMOUNT") {
    return "Amount is at or above ₹80,000. High-value cases wait here even if the playbook wanted a retry.";
  }
  return "Policy held this case before any retry went out.";
}

function shortId(caseId: string) {
  return caseId.length > 22 ? `${caseId.slice(0, 12)}…${caseId.slice(-4)}` : caseId;
}

function scoreLabel(value: number | null | undefined) {
  if (value == null || Number.isNaN(Number(value))) {
    return "Not scored";
  }
  return `${(Number(value) * 100).toFixed(0)}%`;
}

function cleanReason(text: string) {
  if (/[A-Za-z_][\w]*=/.test(text) && text.length > 80) {
    const title = text.split(":")[0]?.trim();
    return title && title.length < 48 ? title : "Agent left a structured fallback note. Use the fields above.";
  }
  return text;
}
