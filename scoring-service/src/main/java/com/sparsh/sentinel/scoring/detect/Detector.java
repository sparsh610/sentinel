package com.sparsh.sentinel.scoring.detect;

import com.sparsh.sentinel.scoring.event.TransactionEvent;

import java.util.Optional;

/**
 * One source of suspicion.
 *
 * <p>Three are rules and two run the ONNX models exported from {@code ml/}: the supervised
 * classifier, and the Isolation Forest judged within a KMeans peer segment. Banks run both
 * kinds: rules encode what a regulator explicitly asks to be monitored, and are explainable line
 * by line; models catch what nobody wrote a rule for.
 */
public interface Detector {

    /**
     * Called after the transaction has been recorded in the scored-transaction ledger, so a
     * detector that looks at history sees the current transaction too.
     */
    Optional<Finding> evaluate(TransactionEvent transaction);
}
