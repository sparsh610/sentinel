package com.sparsh.sentinel.copilot.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the part of ingestion that decides whether an answer can be attributed: the metadata
 * written onto every chunk, and the duplicate guard.
 */
@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private DocumentRepository repository;

    // The real splitter, not a mock: chunking behaviour is part of what is under test.
    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(200)
            .withMinChunkSizeChars(50)
            .withMinChunkLengthToEmbed(5)
            .withMaxNumChunks(10_000)
            .withKeepSeparator(true)
            .build();

    // The real Tika extractor: format handling is part of what these tests exercise.
    private final DocumentTextExtractor extractor = new TikaDocumentTextExtractor();

    private DocumentIngestionService service;

    private DocumentIngestionService service() {
        if (service == null) {
            service = new DocumentIngestionService(vectorStore, splitter, repository, extractor);
        }
        return service;
    }

    /** Service wired with an extractor that always fails, to exercise the failure path. */
    private DocumentIngestionService serviceWithFailingExtractor() {
        DocumentTextExtractor failing = (content, filename) -> {
            throw new DocumentIngestionService.ExtractionFailedException(
                    filename, new IllegalStateException("corrupt container"));
        };
        return new DocumentIngestionService(vectorStore, splitter, repository, failing);
    }

    @Test
    void everyChunkCarriesTheMetadataNeededForACitation() {
        when(repository.findByChecksum(anyString())).thenReturn(Optional.empty());

        byte[] content = ("Article 1. A credit institution shall apply customer due diligence. "
                + "Article 2. Enhanced due diligence applies to politically exposed persons. ")
                .repeat(40)
                .getBytes(StandardCharsets.UTF_8);

        DocumentSummary summary = service().ingest(
                content, "amld.txt", "text/plain", "AMLD VI", Classification.PUBLIC);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        List<Document> chunks = captor.getValue();
        assertThat(chunks).isNotEmpty();
        assertThat(summary.chunkCount()).isEqualTo(chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            var metadata = chunks.get(i).getMetadata();

            assertThat(metadata.get(DocumentIngestionService.META_TITLE)).isEqualTo("AMLD VI");
            assertThat(metadata.get(DocumentIngestionService.META_FILENAME)).isEqualTo("amld.txt");
            assertThat(metadata.get(DocumentIngestionService.META_CLASSIFICATION))
                    .isEqualTo("PUBLIC");
            assertThat(metadata.get(DocumentIngestionService.META_CHUNK_INDEX)).isEqualTo(i);

            // Same document id on every chunk, and it matches what the caller was told.
            assertThat(metadata.get(DocumentIngestionService.META_DOCUMENT_ID))
                    .isEqualTo(summary.id().toString());
        }
    }

    @Test
    void fallsBackToTheFilenameWhenNoTitleIsGiven() {
        when(repository.findByChecksum(anyString())).thenReturn(Optional.empty());

        DocumentSummary summary = service().ingest(
                "Some policy text that is long enough to survive chunking. ".repeat(30)
                        .getBytes(StandardCharsets.UTF_8),
                "internal-policy.txt", "text/plain", "   ", Classification.INTERNAL);

        assertThat(summary.title()).isEqualTo("internal-policy.txt");
    }

    @Test
    void refusesToIngestTheSameBytesTwice() {
        DocumentEntity existing = new DocumentEntity(
                UUID.randomUUID(), "AMLD VI", "amld.txt", "text/plain",
                10, Classification.PUBLIC, 3, "checksum", java.time.Instant.now());

        when(repository.findByChecksum(anyString())).thenReturn(Optional.of(existing));

        byte[] content = "Article 1. Customer due diligence. ".repeat(30)
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service().ingest(
                content, "amld-copy.txt", "text/plain", "AMLD VI copy", Classification.PUBLIC))
                .isInstanceOf(DuplicateDocumentException.class)
                .hasMessageContaining("AMLD VI");

        // Nothing must reach the vector store, or the duplicate guard is pointless.
        verify(vectorStore, never()).add(any());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsAnEmptyFile() {
        assertThatThrownBy(() -> service().ingest(
                new byte[0], "scan.pdf", "application/pdf", "Scan", Classification.PUBLIC))
                .isInstanceOf(DocumentIngestionService.EmptyDocumentException.class)
                .hasMessageContaining("OCR");

        verify(vectorStore, never()).add(any());
    }

    @Test
    void anUnreadableFileFailsWithAMeaningfulErrorAndStoresNothing() {
        when(repository.findByChecksum(anyString())).thenReturn(Optional.empty());

        byte[] corrupt = "not really a pdf".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> serviceWithFailingExtractor().ingest(
                corrupt, "broken.pdf", "application/pdf", "Broken", Classification.PUBLIC))
                .isInstanceOf(DocumentIngestionService.ExtractionFailedException.class)
                .hasMessageContaining("broken.pdf");

        // A half-ingested document is worse than a rejected one: it would be retrievable
        // without ever appearing in the document list.
        verify(vectorStore, never()).add(any());
        verify(repository, never()).save(any());
    }
}
