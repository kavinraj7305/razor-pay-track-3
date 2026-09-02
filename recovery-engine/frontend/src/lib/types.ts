export type PlaybookStep = {
  step: number;
  actionType: string;
  note: string;
};

export type ScorePeek = {
  status: "SCORED" | "LOW_DATA" | "UNAVAILABLE" | string;
  labelledOutcomes: number;
  minLabelledOutcomes: number;
  recoveryProbability: number | null;
  skipRetry: boolean;
  label: string | null;
};

export type Scenario = {
  slug: string;
  eventType: string;
  reason: string;
  intendedAction: string;
  path: string;
};

export type CaseSummary = {
  caseId: string;
  source: string;
  sourceId: string;
  reason: string;
  status: string;
  amountAtRisk: number;
  actionType: string | null;
  actionStatus: string | null;
  recoveryProbability: number | null;
  scoreStatus: string | null;
  playbook: PlaybookStep[];
};

export type PlannedAction = {
  actionId: string;
  actionType: string;
  status: string;
  attemptNumber: number | null;
  note: string | null;
  createdAt: string | null;
};

export type AuditLine = {
  eventId: string;
  eventType: string;
  action: string;
  details: Record<string, unknown> | null;
  createdAt: string | null;
};

export type CaseDetail = CaseSummary & {
  currency: string;
  priority: string;
  merchantId: string | null;
  customerId: string | null;
  createdAt: string | null;
  closedAt: string | null;
  plan: PlannedAction | null;
  actions: PlannedAction[];
  audit: AuditLine[];
  score: ScorePeek | null;
};

export type CaseProposal = {
  caseId: string | null;
  diagnosis: string;
  reasoning: string;
  recommendedAction: string;
  defaultPlaybookAction: string;
  deviatesFromPlaybook: boolean;
  confidence: number;
  mlScore: number | null;
  escalate: boolean;
  actionsAvailable: string[];
  executes: boolean;
  model: string;
  fallbackUsed: boolean;
};

export type OpsPattern = {
  severity: string;
  pattern: string;
  where: string;
  count: number;
  why: string;
  proposedSolution: string;
  relatedCaseIds: string[];
};

export type OpsBriefing = {
  windowHours: number;
  summary: string;
  patterns: OpsPattern[];
  metrics: Record<string, unknown>;
  actionsAvailable: string[];
  executes: boolean;
  fallbackUsed: boolean;
  model: string;
};

export type SimulateResult = {
  stored: boolean;
  scenario: string;
  eventId: string;
  caseId: string | null;
  reason: string;
  status: string;
  actionType: string | null;
  intendedAction: string;
};
