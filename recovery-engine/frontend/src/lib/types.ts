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

export type PolicyPeek = {
  verdict: "ALLOW" | "SKIP_RETRY" | "BLOCK" | string;
  allowExecute: boolean;
  skipRetry: boolean;
  escalate: boolean;
  recommendedAction: string;
  reason: string;
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
  policy: PolicyPeek | null;
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

export type DeskRole = "ADMIN" | "APPROVER" | "OPERATOR";

export type Session = {
  token: string | null;
  userId: string | null;
  email: string | null;
  displayName: string | null;
  role: DeskRole | null;
};

export type DemoAccount = {
  email: string;
  password: string;
  role: DeskRole;
  displayName: string;
  sees: string;
};

export type UserRow = {
  userId: string;
  email: string;
  displayName: string;
  role: DeskRole;
  active: boolean;
  createdAt: string | null;
};

export type ReasonCount = {
  reason: string;
  count: number;
};

export type BenchmarkPath = {
  chases: number;
  recoveredCases: number;
  recoveredInr: number;
  recoveryRate: number;
  wastedChases: number;
  wastedInr: number;
  missedCases: number;
  missedInr: number;
  correctSkips: number;
};

export type BenchmarkReason = {
  reason: string;
  events: number;
  amountAtRiskInr: number;
  baselineRecoveredInr: number;
  aiRecoveredInr: number;
};

export type BenchmarkUnresolved = {
  eventId: string;
  reason: string;
  amountInr: number;
  pRecovery: number;
  why: string;
};

export type BenchmarkReport = {
  ranAt: string;
  merchantId: string;
  seed: number;
  events: number;
  labelledPaid: number;
  amountAtRiskInr: number;
  oracleRecoveredInr: number;
  oracleRate: number;
  baseline: BenchmarkPath;
  ai: BenchmarkPath;
  recoveredDeltaInr: number;
  incrementalPct: number;
  wastedChaseSavedInr: number;
  wastedChasesAvoided: number;
  policyBlocked: number;
  humanEscalations: number;
  mlSkipRetry: number;
  auditCoveragePct: number;
  model: {
    file: string;
    rocAuc: number | null;
    prAuc: number | null;
    f1: number | null;
    threshold: number;
  };
  byReason: BenchmarkReason[];
  unresolved: BenchmarkUnresolved[];
  pitch: string;
};

export type DashboardSnapshot = {
  cases: number;
  open: number;
  recovered: number;
  failed: number;
  pendingApprovals: number;
  amountAtRisk: number;
  adminCount: number;
  approverCount: number;
  operatorCount: number;
  byReason: ReasonCount[];
  users: UserRow[];
};

export type ApprovalItem = {
  caseId: string;
  reason: string;
  status: string;
  amountAtRisk: number;
  policyReason: string;
  recommendedAction: string;
  agentDiagnosis: string;
  agentReasoning: string;
  escalate: boolean;
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

export type WebhookInboxItem = {
  eventId: string;
  eventType: string;
  accountId: string | null;
  intake: "HMAC_SIGNED" | "DESK_SIMULATE" | string;
  signatureVerified: boolean;
  origin: "RAZORPAY" | "LOCAL_SCRIPT" | "DESK_SIMULATE" | string;
  processed: boolean;
  receivedAt: string | null;
  sourceId: string | null;
  caseId: string | null;
  reason: string | null;
};

export type WebhookInboxSnapshot = {
  signedCount: number;
  razorpayCount: number;
  simulateCount: number;
  events: WebhookInboxItem[];
};
