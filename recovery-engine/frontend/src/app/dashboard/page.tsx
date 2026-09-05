"use client";

import { useCallback, useEffect, useState } from "react";
import { DeskChrome, RoleGate } from "@/components/DeskChrome";
import { adminDashboard, assignDeskRole, createDeskUser, inr, prettyError } from "@/lib/api";
import type { DashboardSnapshot, DeskRole } from "@/lib/types";

export default function DashboardPage() {
  return (
    <RoleGate allow={["ADMIN"]}>
      <DeskChrome
        kicker="CEO · overall admin"
        title="Command dashboard"
        blurb="See every open rupee, who is on the desk, and create the two working roles — policy guard and operator."
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
  const [role, setRole] = useState<DeskRole>("OPERATOR");

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
            {snap?.adminCount ?? 0} / {snap?.approverCount ?? 0} / {snap?.operatorCount ?? 0}
          </strong>
          <span className="muted">CEO · guard · desk</span>
        </article>
      </section>

      <div className="desk-split">
        <section className="panel">
          <div className="panel-head">
            <h2>Why money is stuck</h2>
            <span className="muted">{snap?.cases ?? 0} cases</span>
          </div>
          {(snap?.byReason ?? []).length === 0 ? (
            <p className="empty">No demo cases yet. Ask an operator to create the 8-pack on the desk.</p>
          ) : (
            <div className="rows">
              {snap?.byReason.map((row) => (
                <div key={row.reason} className="case-row static">
                  <span className="reason">{row.reason}</span>
                  <span>{row.count}</span>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="panel">
          <div className="panel-head">
            <h2>Create a role</h2>
            <span className="muted">Admin assigns Approver or Operator only</span>
          </div>
          <form
            className="form-grid pad"
            onSubmit={(event) => {
              event.preventDefault();
              void run(async () => {
                await createDeskUser({ email, displayName, password, role });
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
            <label>
              Role
              <select value={role} onChange={(event) => setRole(event.target.value as DeskRole)}>
                <option value="OPERATOR">Operator — add issues + run desk</option>
                <option value="APPROVER">Approver — policy guard in the middle</option>
              </select>
            </label>
            <button className="primary-btn" type="submit" disabled={busy}>
              {busy ? "Saving…" : "Create person"}
            </button>
          </form>
        </section>
      </div>

      <section className="panel">
        <div className="panel-head">
          <h2>Directory</h2>
          <span className="muted">Reassign the two working roles</span>
        </div>
        {(snap?.users ?? []).map((user) => (
          <div key={user.userId} className="user-row">
            <div>
              <strong>{user.displayName}</strong>
              <span className="muted">
                {user.email} · {user.role}
              </span>
            </div>
            {user.role === "ADMIN" ? (
              <span className="badge">CEO</span>
            ) : (
              <div className="role-links">
                <button
                  className="ghost-btn"
                  type="button"
                  disabled={busy || user.role === "APPROVER"}
                  onClick={() => void run(() => assignDeskRole(user.userId, "APPROVER").then(() => undefined))}
                >
                  Make guard
                </button>
                <button
                  className="ghost-btn"
                  type="button"
                  disabled={busy || user.role === "OPERATOR"}
                  onClick={() => void run(() => assignDeskRole(user.userId, "OPERATOR").then(() => undefined))}
                >
                  Make operator
                </button>
              </div>
            )}
          </div>
        ))}
      </section>
    </div>
  );
}
