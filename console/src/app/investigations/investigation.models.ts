/** Mirrors copilot-service's /api/investigations. */

export type InvestigationStatus =
  | 'RUNNING'
  | 'FAILED'
  | 'DRAFTED'
  | 'AWAITING_SIGN_OFF'
  | 'REPORTED'
  | 'CLOSED';

export type Planner = 'FIXED' | 'LLM';

export type StepStatus = 'OK' | 'ERROR' | 'REFUSED';

export type DecisionAction = 'ESCALATE' | 'CLOSE' | 'APPROVE' | 'RETURN';

export interface InvestigationStep {
  stepNo: number;
  tool: string;
  /** JSON: the tool's arguments, plus who asked for the step. */
  input: string;
  output: string | null;
  status: StepStatus;
  durationMs: number;
  startedAt: string;
}

export interface InvestigationDecision {
  actor: string;
  role: 'ANALYST' | 'SENIOR_APPROVER';
  action: DecisionAction;
  comment: string | null;
  decidedAt: string;
}

export interface Investigation {
  id: string;
  alertId: string;
  transactionId: string;
  customerId: string;
  amount: number;
  currency: string;
  planner: Planner;
  status: InvestigationStatus;
  caseNote: string | null;
  noteSource: 'MODEL' | 'TEMPLATE' | null;
  failureReason: string | null;
  startedAt: string;
  finishedAt: string | null;
  steps: InvestigationStep[];
  decisions: InvestigationDecision[];
}

export const STATUS_LABELS: Record<InvestigationStatus, string> = {
  RUNNING: 'Agent working',
  FAILED: 'Failed',
  DRAFTED: 'Awaiting analyst',
  AWAITING_SIGN_OFF: 'Awaiting senior sign-off',
  REPORTED: 'Reported',
  CLOSED: 'Closed - no further action',
};

export const TOOL_LABELS: Record<string, string> = {
  riskScore: 'Read the alert',
  customerHistory: 'Customer history',
  peerSegment: 'Peer segment',
  policyLookup: 'Policy lookup',
  draftCaseNote: 'Draft case note',
};
