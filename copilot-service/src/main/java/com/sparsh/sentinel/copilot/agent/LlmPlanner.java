package com.sparsh.sentinel.copilot.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The optional planner: the local model chooses which evidence tools to call, and in what order.
 *
 * <p>Spring AI's own tool loop is switched off ({@code internalToolExecutionEnabled(false)}): the
 * model only <em>proposes</em> a call, and this loop makes it, through the same
 * {@link StepRecorder} as the fixed plan. So the step limit and the trace hold whatever the model
 * asks for, and a model that loops cannot run past the limit.
 *
 * <p>The tools it is given take no identifiers. They are bound to this alert's customer and
 * transaction, so no prompt - including text injected into a policy excerpt - can point the
 * agent at another customer's data.
 *
 * <p>A small local model does not always call every tool. Whatever it leaves out is then filled
 * in by the fixed plan, marked as such in the trace, so the note never lacks evidence because the
 * model forgot to ask.
 */
@Component
public class LlmPlanner {

    static final String BY_MODEL = "model";
    private static final String BY_FALLBACK = "plan (model did not ask)";
    private static final Logger log = LoggerFactory.getLogger(LlmPlanner.class);

    private static final String SYSTEM = """
            You are gathering evidence for an anti-money-laundering analyst about one alert.
            Call the tools you need: customerHistory, peerSegment, and policyLookup with a short
            search phrase for the policy that applies to the alert's findings. Call each at most
            once. When you have called them, reply with the single word DONE.
            """;

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final FixedPlanner plan;
    private final AgentProperties properties;

    public LlmPlanner(ChatModel chatModel, ToolCallingManager toolCallingManager, FixedPlanner plan,
                      AgentProperties properties) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.plan = plan;
        this.properties = properties;
    }

    void gather(Evidence evidence, StepRecorder steps) {
        try {
            converse(evidence, steps);
        } catch (RuntimeException e) {
            log.warn("LLM planner failed, falling back to the fixed plan: {}", e.getMessage());
            evidence.gaps.add("The model planner was unavailable (" + e.getClass().getSimpleName()
                    + "); the fixed plan gathered the evidence instead.");
        }
        if (evidence.history == null && steps.evidenceBudgetLeft()) {
            plan.customerHistory(evidence, steps, BY_FALLBACK);
        }
        if (evidence.peerSegment == null && steps.evidenceBudgetLeft()) {
            plan.peerSegment(evidence, steps, BY_FALLBACK);
        }
        if (evidence.policy.isEmpty() && steps.evidenceBudgetLeft()) {
            plan.policyLookup(evidence, steps, BY_FALLBACK, InvestigationTools.policyQuestionFor(evidence.alert));
        }
    }

    private void converse(Evidence evidence, StepRecorder steps) {
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .model(properties.model())
                .temperature(0.0)
                .toolCallbacks(List.of(ToolCallbacks.from(new BoundTools(evidence, steps))))
                .internalToolExecutionEnabled(false)
                .build();
        Prompt prompt = new Prompt(List.of(new SystemMessage(SYSTEM), new UserMessage(evidence.describe())), options);

        ChatResponse response = chatModel.call(prompt);
        // Each round asks for at least one call, and every call spends budget or is refused, so
        // this ends; the round cap only stops a model that keeps asking after being refused.
        int rounds = 0;
        while (response.hasToolCalls() && steps.evidenceBudgetLeft() && rounds++ < properties.maxSteps()) {
            ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, response);
            prompt = new Prompt(result.conversationHistory(), options);
            response = chatModel.call(prompt);
        }
    }

    /** The tools as the model sees them, bound to this one alert. */
    public final class BoundTools {

        private final Evidence evidence;
        private final StepRecorder steps;

        BoundTools(Evidence evidence, StepRecorder steps) {
            this.evidence = evidence;
            this.steps = steps;
        }

        @Tool(name = InvestigationTools.CUSTOMER_HISTORY,
                description = "The customer's recent transactions: totals in and out, cash count, counterparties.")
        public String customerHistory() {
            plan.customerHistory(evidence, steps, BY_MODEL);
            return evidence.history == null ? "Not available." : evidence.history.toString();
        }

        @Tool(name = InvestigationTools.PEER_SEGMENT,
                description = "Whether the alerted transaction is unusual for its peer segment.")
        public String peerSegment() {
            plan.peerSegment(evidence, steps, BY_MODEL);
            return evidence.peerSegment == null ? "Not available." : evidence.peerSegment.toString();
        }

        @Tool(name = InvestigationTools.POLICY_LOOKUP,
                description = "Search the bank's anti-money-laundering policy for the clauses that apply.")
        public String policyLookup(@ToolParam(description = "A short search phrase, e.g. 'structuring reporting threshold'")
                            String query) {
            plan.policyLookup(evidence, steps, BY_MODEL, query);
            return evidence.policy.isEmpty() ? "No clause found." : evidence.policy.size() + " clauses found.";
        }
    }
}
