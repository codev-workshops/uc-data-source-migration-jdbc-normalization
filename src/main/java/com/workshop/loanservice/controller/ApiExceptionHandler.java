package com.workshop.loanservice.controller;

import com.workshop.loanservice.service.LoanNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns service failures into responses that say what went wrong without repeating what the caller
 * sent. Reflecting request input into an error body is the cheapest way to hand an attacker an XSS
 * or log-injection vector, and it was how a missing loan id used to surface.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(LoanNotFoundException.class)
    public ProblemDetail handleNotFound(LoanNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        // Spring's own web exceptions already carry the right status - an unmapped URL is a 404, not
        // an internal error - so they keep their response instead of being flattened into a 500.
        if (e instanceof ErrorResponse errorResponse) {
            return errorResponse.getBody();
        }
        // Everything else gets a deliberately generic detail. The stack trace belongs in the log,
        // correlated by trace id, not in a response body.
        log.error("Unhandled request failure", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
    }
}
