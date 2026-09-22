package com.sparsh.sentinel.copilot.chat;

import java.util.UUID;

/**
 * A source the answer was built from. The {@code marker} is what the model is told to write
 * inline, so the UI can turn "[2]" in the answer text into a link to this citation.
 *
 * @param excerpt the chunk text itself. Returned deliberately: an analyst has to be able to
 *                read the paragraph rather than trust a summary of it.
 */
public record Citation(
        int marker,
        UUID documentId,
        String title,
        int chunkIndex,
        double score,
        String excerpt
) {
}
