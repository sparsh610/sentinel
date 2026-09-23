package com.sparsh.sentinel.scoring.detect;

import com.sparsh.sentinel.scoring.event.TransactionEvent;

import java.util.Optional;

/**
 * One source of suspicion.
 *
 * <p>This week every detector is a rule. In week 4 the ONNX models - the supervised classifier,
 * the Isolation Forest - arrive as more implementations of this interface, and the rules stay.
 * Banks run both: rules encode what a regulator explicitly asks to be monitored, and are
 * explainable line by line; models catch what nobody wrote a rule for.
 */
public interface Detector {

    /**
     * Called after the transaction has been recorded in the scored-transaction ledger, so a
     * detector that looks at history sees the current transaction too.
     */
    Optional<Finding> evaluate(TransactionEvent transaction);
}
