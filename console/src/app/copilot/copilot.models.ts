/** Mirrors the copilot-service API. Kept in one place so a contract change breaks compilation. */

export type Classification = 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL';

export interface Citation {
  /** The number the answer cites inline as [1], [2]. */
  marker: number;
  documentId: string;
  title: string;
  chunkIndex: number;
  score: number;
  excerpt: string;
}

export interface DocumentSummary {
  id: string;
  title: string;
  filename: string;
  classification: Classification;
  chunkCount: number;
  sizeBytes: number;
  uploadedAt: string;
}

export interface Exchange {
  question: string;
  /** Grows token by token while the answer streams in. */
  answer: string;
  citations: Citation[];
  /**
   * False when retrieval found nothing above the similarity threshold. Rendered differently on
   * purpose: an ungrounded reply is a refusal, not a result, and must not look like one.
   */
  grounded: boolean;
  streaming: boolean;
  error?: string;
}
