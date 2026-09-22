package com.sparsh.sentinel.copilot.document;

import java.util.UUID;

/**
 * Thrown when the same bytes have already been ingested. Re-ingesting would duplicate every
 * chunk and quietly bias retrieval towards whichever document was uploaded twice.
 */
public class DuplicateDocumentException extends RuntimeException {

    private final UUID existingDocumentId;

    public DuplicateDocumentException(UUID existingDocumentId, String title) {
        super("Already ingested as '" + title + "' (" + existingDocumentId + ")");
        this.existingDocumentId = existingDocumentId;
    }

    public UUID getExistingDocumentId() {
        return existingDocumentId;
    }
}
