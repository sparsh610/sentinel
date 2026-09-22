package com.sparsh.sentinel.copilot.document;

/**
 * How sensitive a document is. Enforced at retrieval time from week 7, so that a chunk of a
 * confidential policy can never reach a user who is not entitled to it.
 *
 * <p>It is set at ingestion and never defaulted. A document with no classification is a
 * document nobody has decided about, and guessing is how confidential material ends up quoted
 * in an answer.
 */
public enum Classification {

    /** Published regulation - AMLD, PSD2, GDPR. Anyone may see it. */
    PUBLIC,

    /** Internal policy or procedure. Any authenticated analyst may see it. */
    INTERNAL,

    /** Restricted material. Senior approvers only. */
    CONFIDENTIAL
}
