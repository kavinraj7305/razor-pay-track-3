"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { listDemoAccounts, login, prettyError } from "@/lib/api";
import { getSession, homeFor, setSession } from "@/lib/session";
import type { DemoAccount, DeskRole } from "@/lib/types";

export default function LoginPage() {
  const router = useRouter();
  const [accounts, setAccounts] = useState<DemoAccount[]>([]);
  const [email, setEmail] = useState("ceo@recovery.local");
  const [password, setPassword] = useState("admin123");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const session = getSession();
    if (session) {
      router.replace(homeFor(session.role));
      return;
    }
    void listDemoAccounts()
      .then(setAccounts)
      .catch(() =>
        setAccounts([
          {
            email: "ceo@recovery.local",
            password: "admin123",
            role: "ADMIN",
            displayName: "Priya Shah · CEO",
            sees: "Everything",
          },
          {
            email: "policy@recovery.local",
            password: "approve123",
            role: "APPROVER",
            displayName: "Arjun Mehta · Policy guard",
            sees: "Approval queue",
          },
          {
            email: "desk@recovery.local",
            password: "operate123",
            role: "OPERATOR",
            displayName: "Neha Iyer · Recovery desk",
            sees: "Create and run cases",
          },
        ]),
      );
  }, [router]);

  async function submit(nextEmail = email, nextPassword = password) {
    setBusy(true);
    setError(null);
    try {
      const session = await login(nextEmail, nextPassword);
      if (!session.token || !session.role) {
        throw new Error("login did not return a session");
      }
      setSession({
        token: session.token,
        userId: session.userId ?? "",
        email: session.email ?? nextEmail,
        displayName: session.displayName ?? nextEmail,
        role: session.role,
      });
      router.replace(homeFor(session.role));
    } catch (err) {
      setError(prettyError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="desk">
      <header className="desk-bar">
        <div>
          <p className="pill">Enterprise recovery · three roles</p>
          <h1>Sign in</h1>
          <p>CEO sees everything. Policy guard approves blocks. Desk operator creates cases and runs the playbook.</p>
        </div>
      </header>
      <div className="wrap login-wrap">
        {error ? <div className="err">{error}</div> : null}
        <div className="role-cards">
          {accounts.map((account) => (
            <button
              key={account.email}
              type="button"
              className="role-card"
              disabled={busy}
              onClick={() => {
                setEmail(account.email);
                setPassword(account.password);
                void submit(account.email, account.password);
              }}
            >
              <p className="pill">{labelFor(account.role)}</p>
              <strong>{account.displayName}</strong>
              <span className="muted">{account.sees}</span>
              <span className="muted">
                {account.email} · {account.password}
              </span>
            </button>
          ))}
        </div>
        <form
          className="panel login-form"
          onSubmit={(event) => {
            event.preventDefault();
            void submit();
          }}
        >
          <div className="panel-head">
            <h2>Or type credentials</h2>
          </div>
          <div className="form-grid">
            <label>
              Email
              <input value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="username" />
            </label>
            <label>
              Password
              <input
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
              />
            </label>
          </div>
          <button className="primary-btn" type="submit" disabled={busy}>
            {busy ? "Signing in…" : "Enter workspace"}
          </button>
        </form>
      </div>
    </div>
  );
}

function labelFor(role: DeskRole) {
  if (role === "ADMIN") {
    return "CEO · Admin";
  }
  if (role === "APPROVER") {
    return "Human in the loop";
  }
  return "Operator";
}
