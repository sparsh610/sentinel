package com.sparsh.sentinel.copilot.error;

import com.sparsh.sentinel.copilot.agent.client.ServiceUnavailableException;
import com.sparsh.sentinel.copilot.document.DocumentIngestionService;
import com.sparsh.sentinel.copilot.document.DuplicateDocumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Maps the failures a caller can actually do something about onto RFC 7807 problem details.
 * Anything not listed here stays a 500 and is logged, rather than leaking an internal message
 * to the client.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DuplicateDocumentException.class)
    ProblemDetail onDuplicate(DuplicateDocumentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Document already ingested");
        problem.setType(URI.create("urn:sentinel:document-duplicate"));
        problem.setProperty("existingDocumentId", e.getExistingDocumentId());
        return problem;
    }

    @ExceptionHandler(DocumentIngestionService.EmptyDocumentException.class)
    ProblemDetail onEmpty(DocumentIngestionService.EmptyDocumentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("No extractable text");
        problem.setType(URI.create("urn:sentinel:document-empty"));
        return problem;
    }

    @ExceptionHandler(DocumentIngestionService.ExtractionFailedException.class)
    ProblemDetail onExtractionFailed(DocumentIngestionService.ExtractionFailedException e) {
        log.warn("Extraction failed: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Could not read the document");
        problem.setType(URI.create("urn:sentinel:document-unreadable"));
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onIllegalArgument(IllegalArgumentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    /** A decision the case's lifecycle does not allow - deciding twice, or signing off your own escalation. */
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail onConflict(IllegalStateException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Not allowed in the case's current state");
        problem.setType(URI.create("urn:sentinel:investigation-conflict"));
        return problem;
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    ProblemDetail onServiceUnavailable(ServiceUnavailableException e) {
        log.warn("{} unreachable: {}", e.getService(), e.getCause() == null ? "" : e.getCause().getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                e.getService() + " is not reachable. Is it running?");
        problem.setTitle("Service unavailable");
        problem.setType(URI.create("urn:sentinel:service-unavailable"));
        return problem;
    }

    /** Another Sentinel service refused a call, e.g. a status move its lifecycle does not allow. */
    @ExceptionHandler(org.springframework.web.client.RestClientResponseException.class)
    ProblemDetail onDownstreamRefusal(org.springframework.web.client.RestClientResponseException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "A Sentinel service refused the call: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        problem.setTitle("Downstream service refused");
        return problem;
    }

    /**
     * The model endpoint being down is the most common runtime failure here, and it is an
     * operational problem rather than a bad request - 503 tells the UI to offer a retry.
     */
    @ExceptionHandler(org.springframework.web.client.ResourceAccessException.class)
    ProblemDetail onModelUnreachable(org.springframework.web.client.ResourceAccessException e) {
        log.warn("Model endpoint unreachable: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The language model endpoint is not reachable. Is Ollama running? "
                        + "docker compose --profile llm up -d");
        problem.setTitle("Model unavailable");
        problem.setType(URI.create("urn:sentinel:model-unavailable"));
        return problem;
    }
}
