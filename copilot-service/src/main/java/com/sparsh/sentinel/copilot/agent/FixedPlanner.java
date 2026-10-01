package com.sparsh.sentinel.copilot.agent;

import org.springframework.stereotype.Component;

import java.util.Map;

import static com.sparsh.sentinel.copilot.agent.InvestigationTools.CUSTOMER_HISTORY;
import static com.sparsh.sentinel.copilot.agent.InvestigationTools.PEER_SEGMENT;
import static com.sparsh.sentinel.copilot.agent.InvestigationTools.POLICY_LOOKUP;

/**
 * The default planner: the same evidence, in the same order, for every alert - the shape of a
 * bank's investigation playbook. Predictable, cheap, and needs no model.
 *
 * <p>Its three steps are also how the LLM planner fills a gap the model left.
 */
@Component
public class FixedPlanner {

    /** Who asked for a step, shown in the trace. */
    static final String BY_PLAN = "plan";

    private final InvestigationTools tools;

    public FixedPlanner(InvestigationTools tools) {
        this.tools = tools;
    }

    void gather(Evidence evidence, StepRecorder steps) {
        customerHistory(evidence, steps, BY_PLAN);
        peerSegment(evidence, steps, BY_PLAN);
        policyLookup(evidence, steps, BY_PLAN, InvestigationTools.policyQuestionFor(evidence.alert));
    }

    void customerHistory(Evidence evidence, StepRecorder steps, String requestedBy) {
        String customerId = evidence.alert.customerId();
        steps.run(CUSTOMER_HISTORY, Map.of("customerId", customerId, "requestedBy", requestedBy),
                        () -> tools.customerHistory(customerId))
                .ifPresentOrElse(history -> evidence.history = history,
                        () -> evidence.gaps.add("The customer's transaction history could not be read."));
    }

    void peerSegment(Evidence evidence, StepRecorder steps, String requestedBy) {
        var transactionId = evidence.alert.transactionId();
        steps.run(PEER_SEGMENT, Map.of("transactionId", transactionId, "requestedBy", requestedBy),
                        () -> tools.peerSegment(transactionId))
                .ifPresentOrElse(segment -> evidence.peerSegment = segment,
                        () -> evidence.gaps.add("The peer segment is not available (are the models exported?)."));
    }

    void policyLookup(Evidence evidence, StepRecorder steps, String requestedBy, String question) {
        steps.run(POLICY_LOOKUP, Map.of("question", question, "requestedBy", requestedBy),
                        () -> tools.policyLookup(question))
                .filter(found -> !found.isEmpty())
                .ifPresentOrElse(evidence::addPolicy,
                        () -> evidence.gaps.add("No policy clause matched \"" + question + "\"."));
    }
}
