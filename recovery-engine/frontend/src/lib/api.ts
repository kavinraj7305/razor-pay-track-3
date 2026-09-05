import { getSession, setSession } from "./session";
import type {
  ApprovalItem,
  BenchmarkReport,
  CaseDetail,
  CaseProposal,
  CaseSummary,
  DashboardSnapshot,
  DemoAccount,
  DeskRole,
  OpsBriefing,
  PlatformStatus,
  Scenario,
  Session,
  SimulateResult,
  UserRow,
  WebhookInboxSnapshot,
} from "./types";

function authHeaders(): HeadersInit {
  const session = getSession();
  return session?.token ? { Authorization: `Bearer ${session.token}` } : {};
}

async function readJson<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  const extra = authHeaders();
  Object.entries(extra).forEach(([key, value]) => headers.set(key, value));
  const response = await fetch(path, { cache: "no-store", ...init, headers });
  if (response.status === 401 && !path.startsWith("/api/auth/login")) {
    setSession(null);
    if (typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
      window.location.replace("/login");
    }
  }
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `${response.status} ${path}`);
  }
  return response.json() as Promise<T>;
}

export async function listDemoAccounts() {
  const rows = await readJson<DemoAccount[]>("/api/auth/demo");
  return rows.filter((account) => account.role === "ADMIN" || account.role === "APPROVER");
}

export function login(email: string, password: string) {
  return readJson<Session>("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
}

export function logout() {
  return readJson<Session>("/api/auth/logout", { method: "POST" });
}

export function me() {
  return readJson<Session>("/api/auth/me");
}

export function adminDashboard() {
  return readJson<DashboardSnapshot>("/api/admin/dashboard");
}

export function adminBenchmark() {
  return readJson<BenchmarkReport>("/api/admin/benchmark");
}

export function adminPlatform() {
  return readJson<PlatformStatus>("/api/admin/platform");
}

export function createDeskUser(payload: {
  email: string;
  displayName: string;
  password: string;
  role: DeskRole;
}) {
  return readJson<UserRow>("/api/admin/users", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function assignDeskRole(userId: string, role: DeskRole) {
  return readJson<UserRow>(`/api/admin/users/${userId}/role`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ role }),
  });
}

export function pendingApprovals() {
  return readJson<ApprovalItem[]>("/api/approvals/pending");
}

export function approveCase(caseId: string, note: string) {
  return readJson<CaseDetail>(`/api/approvals/${caseId}/approve`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ note }),
  });
}

export function rejectCase(caseId: string, note: string) {
  return readJson<CaseDetail>(`/api/approvals/${caseId}/reject`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ note }),
  });
}

export function webhookInbox() {
  return readJson<WebhookInboxSnapshot>("/api/webhooks/inbox");
}

export function listScenarios() {
  return readJson<Scenario[]>("/api/webhooks/simulate");
}

export function listCases() {
  return readJson<CaseSummary[]>("/api/recovery-cases");
}

export function getCase(caseId: string) {
  return readJson<CaseDetail>(`/api/recovery-cases/${caseId}`);
}

export function createIssue(slug: string) {
  return readJson<SimulateResult>(`/api/webhooks/simulate/${slug}`);
}

export function createAllIssues() {
  return readJson<SimulateResult[]>("/api/webhooks/simulate/all");
}

export function executeNext(caseId: string) {
  return readJson<CaseDetail>(`/api/recovery-cases/${caseId}/execute`, { method: "POST" });
}

export function proposeCase(caseId: string) {
  return readJson<CaseProposal>("/agent-api/propose", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ caseId }),
    signal: AbortSignal.timeout(60000),
  });
}

export function opsBriefing(windowHours = 6) {
  return readJson<OpsBriefing>("/agent-api/ops/briefing", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ windowHours }),
    signal: AbortSignal.timeout(60000),
  });
}

export function recordAgentProposal(caseId: string, proposal: CaseProposal) {
  return readJson<CaseDetail>(`/api/recovery-cases/${caseId}/agent-proposal`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(proposal),
    signal: AbortSignal.timeout(60000),
  });
}

export function inr(amount: number | null | undefined) {
  if (amount == null || Number.isNaN(Number(amount))) {
    return "—";
  }
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(Number(amount));
}

export function pct(value: number | null | undefined) {
  if (value == null) {
    return "—";
  }
  return `${(value * 100).toFixed(0)}%`;
}

export function prettyError(err: unknown) {
  if (!(err instanceof Error)) {
    return "Request failed";
  }
  try {
    const parsed = JSON.parse(err.message) as { error?: string };
    if (parsed.error) {
      return parsed.error;
    }
  } catch {
    /* keep raw */
  }
  return err.message;
}
