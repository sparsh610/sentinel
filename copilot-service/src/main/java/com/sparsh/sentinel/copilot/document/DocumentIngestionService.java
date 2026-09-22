package com.sparsh.sentinel.copilot.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns an uploaded file into retrievable, attributable chunks.
 *
 * <p>The attributability is the point. Every chunk carries the document id, title and its
 * position in the document, because an answer that cannot name the paragraph it came from is
 * useless to a compliance analyst.
 */
@Service
public class DocumentIngestionService {

    /** Metadata keys written onto every chunk. Read back when building citations. */
    public static final String META_DOCUMENT_ID = "documentId";
    public static final String META_TITLE = "title";
    public static final String META_FILENAME = "filename";
    public static final String META_CLASSIFICATION = "classification";
    public static final String META_CHUNK_INDEX = "chunkIndex";

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;
    private final DocumentRepository repository;
    private final DocumentTextExtractor extractor;

    public DocumentIngestionService(VectorStore vectorStore,
                                    TokenTextSplitter splitter,
                                    DocumentRepository repository,
                                    DocumentTextExtractor extractor) {
        this.vectorStore = vectorStore;
        this.splitter = splitter;
        this.repository = repository;
        this.extractor = extractor;
    }

    /**
     * Extracts, chunks, embeds and stores a document.
     *
     * <p>Not atomic with the vector store, which has no transaction: if the database commit
     * fails after the chunks are written, the chunks are orphaned. That is tolerable while the
     * checksum makes re-ingestion a no-op, and is the kind of thing a reconciliation job
     * handles properly later.
     *
     * @throws DuplicateDocumentException if these exact bytes were ingested before
     */
    @Transactional
    public DocumentSummary ingest(byte[] content,
                                  String filename,
                                  String contentType,
                                  String title,
                                  Classification classification) {

        if (content.length == 0) {
            throw new EmptyDocumentException(filename);
        }

        String checksum = sha256(content);

        repository.findByChecksum(checksum).ifPresent(existing -> {
            throw new DuplicateDocumentException(existing.getId(), existing.getTitle());
        });

        UUID documentId = UUID.randomUUID();
        String resolvedTitle = (title == null || title.isBlank()) ? filename : title.strip();

        List<Document> extracted = extractor.extract(content, filename);
        List<Document> chunks = splitter.apply(extracted);

        if (chunks.isEmpty()) {
            throw new EmptyDocumentException(filename);
        }

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = chunks.get(i).getMetadata();
            metadata.put(META_DOCUMENT_ID, documentId.toString());
            metadata.put(META_TITLE, resolvedTitle);
            metadata.put(META_FILENAME, filename);
            metadata.put(META_CLASSIFICATION, classification.name());
            metadata.put(META_CHUNK_INDEX, i);
        }

        vectorStore.add(chunks);

        DocumentEntity entity = new DocumentEntity(
                documentId,
                resolvedTitle,
                filename,
                contentType,
                content.length,
                classification,
                chunks.size(),
                checksum,
                Instant.now()
        );
        repository.save(entity);

        log.info("Ingested '{}' ({}) as {} chunks, classification {}",
                resolvedTitle, filename, chunks.size(), classification);

        return DocumentSummary.of(entity);
    }

    @Transactional(readOnly = true)
    public List<DocumentSummary> list() {
        return repository.findAll().stream().map(DocumentSummary::of).toList();
    }

    private static String sha256(byte[] content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK specification", e);
        }
    }

    /** A file that parsed cleanly but produced no usable text - a scanned PDF, typically. */
    public static class EmptyDocumentException extends RuntimeException {
        public EmptyDocumentException(String filename) {
            super("No extractable text in '" + filename
                    + "'. If it is a scanned PDF it needs OCR before ingestion.");
        }
    }

    /** A file Tika could not parse at all - corrupt, or not the format it claims to be. */
    public static class ExtractionFailedException extends RuntimeException {
        public ExtractionFailedException(String filename, Throwable cause) {
            super("Could not extract text from '" + filename + "': " + cause.getMessage(), cause);
        }
    }
}
