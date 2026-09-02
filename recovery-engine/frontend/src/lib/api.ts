import type { CaseDetail, CaseSummary, Scenario, SimulateResult } from "./types";

async function readJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, { cache: "no-store", ...init });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `${response.status} ${path}`);
  }
  return response.json() as Promise<T>;
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
