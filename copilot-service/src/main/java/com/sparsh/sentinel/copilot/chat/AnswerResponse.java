package com.sparsh.sentinel.copilot.chat;

import java.util.List;

/**
 * @param groundedInSources false when retrieval found nothing above the similarity threshold.
 *                          The UI shows this differently: an ungrounded answer is a refusal,
 *                          not a result, and must not look like one.
 */
public record AnswerResponse(
        String answer,
        List<Citation> citations,
        boolean groundedInSources
) {
}
