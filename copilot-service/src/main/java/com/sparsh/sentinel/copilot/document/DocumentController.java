package com.sparsh.sentinel.copilot.document;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    public DocumentController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * Uploads a policy document.
     *
     * <p>{@code classification} has no default on purpose. Defaulting it to PUBLIC would mean a
     * careless upload silently makes a confidential policy answerable to everyone.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentSummary upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam("classification") Classification classification,
                                  @RequestParam(value = "title", required = false) String title) {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        try {
            return ingestionService.ingest(
                    file.getBytes(),
                    file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename(),
                    file.getContentType() == null
                            ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType(),
                    title,
                    classification);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }

    @GetMapping
    public List<DocumentSummary> list() {
        return ingestionService.list();
    }
}
