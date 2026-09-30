/** Mirrors the scoring-service and tx-ingest APIs. Kept in one place so a contract change breaks compilation. */

export type Rule =
  | 'LARGE_CASH'
  | 'STRUCTURING'
  | 'HIGH_RISK_JURISDICTION'
  | 'ML_CLASSIFIER'
  | 'ML_ANOMALY';

export type AlertStatus = 'OPEN' | 'IN_REVIEW' | 'ESCALATED' | 'CLOSED';

export interface AlertFinding {
  rule: Rule;
  score: number;
  /** The sentence the analyst reads - names the facts that triggered the rule. */
  reason: string;
}

export interface Alert {
  id: string;
  transactionId: string;
  customerId: string;
  customerSegment: string;
  direction: 'DEBIT' | 'CREDIT';
  channel: 'CARD' | 'TRANSFER' | 'CASH';
  amount: number;
  currency: string;
  counterpartyName: string | null;
  counterpartyCountry: string | null;
  bookedAt: string;
  score: number;
  status: AlertStatus;
  raisedAt: string;
  findings: AlertFinding[];
}

export interface SimulationReport {
  submitted: number;
  planted: string[];
}

export const RULE_LABELS: Record<Rule, string> = {
  LARGE_CASH: 'Large cash',
  STRUCTURING: 'Structuring',
  HIGH_RISK_JURISDICTION: 'High-risk jurisdiction',
  ML_CLASSIFIER: 'Model: known pattern',
  ML_ANOMALY: 'Model: unusual for peers',
};
