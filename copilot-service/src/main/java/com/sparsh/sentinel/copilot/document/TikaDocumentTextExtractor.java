package com.sparsh.sentinel.copilot.document;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tika-backed extraction. Handles PDF, Word, HTML and plain text, which covers how regulatory
 * material actually arrives.
 */
@Component
public class TikaDocumentTextExtractor implements DocumentTextExtractor {

    @Override
    public List<Document> extract(byte[] content, String filename) {
        try {
            return new TikaDocumentReader(new NamedByteArrayResource(content, filename)).get();
        } catch (RuntimeException e) {
            // Tika wraps parser failures in a bare RuntimeException. Left alone it reaches the
            // caller as a 500, which tells whoever uploaded a corrupt file nothing useful.
            throw new DocumentIngestionService.ExtractionFailedException(filename, e);
        }
    }

    /**
     * Tika chooses its parser partly from the filename, so a {@link ByteArrayResource} without
     * one degrades PDFs and Word files to plain-text guessing.
     */
    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] content, String filename) {
            super(content);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
