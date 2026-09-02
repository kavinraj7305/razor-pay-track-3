import type { CaseDetail, PlaybookStep } from "./types";

const DONE = new Set(["EXECUTED", "FAILED", "CANCELLED"]);

export function completedSteps(detail: CaseDetail): Set<number> {
  const done = new Set<number>();
  for (const action of detail.actions ?? []) {
    if (action.attemptNumber == null) {
      continue;
    }
    if (DONE.has(action.status)) {
      done.add(action.attemptNumber);
    }
  }
  return done;
}

export function nextPlaybookStep(detail: CaseDetail): PlaybookStep | null {
  const done = completedSteps(detail);
  const steps = [...(detail.playbook ?? [])].sort((a, b) => a.step - b.step);
  return steps.find((step) => !done.has(step.step)) ?? null;
}

/** Ordered playbook steps not yet finished — each should run at most once. */
export function remainingSteps(detail: CaseDetail): PlaybookStep[] {
  const done = completedSteps(detail);
  return [...(detail.playbook ?? [])]
    .sort((a, b) => a.step - b.step)
    .filter((step) => !done.has(step.step));
}

export function progressLabel(detail: CaseDetail): string {
  const total = detail.playbook?.length ?? 0;
  const done = completedSteps(detail).size;
  if (detail.status === "RECOVERED") {
    return "Complete · recovered";
  }
  if (total === 0) {
    return "No playbook";
  }
  if (done >= total) {
    return `Complete · ${done}/${total} steps`;
  }
  return `Step ${done + 1} of ${total}`;
}

export function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
