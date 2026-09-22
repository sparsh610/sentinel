package com.sparsh.sentinel.copilot.document;

import java.time.Instant;
import java.util.UUID;

/** What the API returns about a document. Deliberately not the entity. */
public record DocumentSummary(
        UUID id,
        String title,
        String filename,
        Classification classification,
        int chunkCount,
        long sizeBytes,
        Instant uploadedAt
) {

    static DocumentSummary of(DocumentEntity entity) {
        return new DocumentSummary(
                entity.getId(),
                entity.getTitle(),
                entity.getFilename(),
                entity.getClassification(),
                entity.getChunkCount(),
                entity.getSizeBytes(),
                entity.getUploadedAt()
        );
    }
}
