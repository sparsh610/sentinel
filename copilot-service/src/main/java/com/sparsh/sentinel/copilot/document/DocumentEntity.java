package com.sparsh.sentinel.copilot.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * What the application knows about an ingested document as a whole. The chunks and their
 * embeddings live in {@code knowledge.document_chunk}, owned by the vector store.
 */
@Entity
@Table(name = "document", schema = "knowledge")
public class DocumentEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(nullable = false, length = 512)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Classification classification;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected DocumentEntity() {
        // for JPA
    }

    public DocumentEntity(UUID id, String title, String filename, String contentType,
                          long sizeBytes, Classification classification, int chunkCount,
                          String checksum, Instant uploadedAt) {
        this.id = id;
        this.title = title;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.classification = classification;
        this.chunkCount = chunkCount;
        this.checksum = checksum;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Classification getClassification() {
        return classification;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public String getChecksum() {
        return checksum;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
