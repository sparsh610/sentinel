package com.sparsh.sentinel.copilot.document;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Pulls text out of an uploaded file.
 *
 * <p>A seam rather than a call to Tika inline, for two reasons: the failure path is otherwise
 * impossible to test, and OCR for scanned PDFs will slot in here rather than inside the
 * ingestion flow.
 */
public interface DocumentTextExtractor {

    /**
     * @throws DocumentIngestionService.ExtractionFailedException if the file cannot be parsed
     */
    List<Document> extract(byte[] content, String filename);
}
