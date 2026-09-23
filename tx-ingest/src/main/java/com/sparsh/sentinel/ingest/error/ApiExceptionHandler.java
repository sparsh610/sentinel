package com.sparsh.sentinel.ingest.error;

import com.sparsh.sentinel.ingest.transaction.UnknownCustomerException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Map;
import java.util.TreeMap;

/**
 * Maps the failures a sender can act on onto RFC 7807 problem details. Anything not listed here
 * stays a 500 rather than leaking an internal message.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Every invalid field at once, so a sender fixes a payload in one round trip, not five. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onInvalidBody(MethodArgumentNotValidException e) {
        Map<String, String> errors = new TreeMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "The transaction is not valid");
        problem.setTitle("Invalid transaction");
        problem.setType(URI.create("urn:sentinel:transaction-invalid"));
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail onInvalidParameter(ConstraintViolationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(UnknownCustomerException.class)
    ProblemDetail onUnknownCustomer(UnknownCustomerException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Unknown customer");
        problem.setType(URI.create("urn:sentinel:customer-unknown"));
        problem.setProperty("customerId", e.getCustomerId());
        return problem;
    }

    /**
     * Two concurrent submissions of the same {@code externalRef} both pass the replay check;
     * the unique constraint stops the second. Telling that sender to retry lands it on the
     * normal replay path.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail onConflict(DataIntegrityViolationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "A concurrent request stored the same transaction. Retry to fetch it.");
        problem.setTitle("Concurrent duplicate");
        problem.setType(URI.create("urn:sentinel:transaction-conflict"));
        return problem;
    }
}
