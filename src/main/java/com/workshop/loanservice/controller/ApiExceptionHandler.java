package com.workshop.loanservice.controller;

import com.workshop.loanservice.service.ResourceNotFoundException;
import com.workshop.loanservice.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;

/**
 * One JSON error shape for every failure: {@code timestamp, status, error, message, path, requestId}.
 * Messages are written here or taken from Spring's client-error details; exception text and stack traces of
 * unexpected errors are only logged, never returned.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ApiError(OffsetDateTime timestamp, int status, String error, String message, String path,
                           String requestId) {
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, HttpHeaders.EMPTY, e.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY, "Invalid value for parameter '" + e.getName() + "'", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY, "Malformed request body", request);
    }

    /**
     * Spring MVC exceptions that carry their own status (unknown route 404, 405, missing parameter 400, 406, 415, ...)
     * keep it; anything else is an unexpected 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> other(Exception e, HttpServletRequest request) {
        if (e instanceof ErrorResponse response && !response.getStatusCode().is5xxServerError()) {
            String detail = response.getBody().getDetail();
            log.debug("{} {} -> {}: {}", request.getMethod(), request.getRequestURI(), response.getStatusCode(), detail);
            return error(response.getStatusCode(), response.getHeaders(),
                    detail != null ? detail : reason(response.getStatusCode()), request);
        }
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY,
                "Unexpected error; quote the requestId when reporting it", request);
    }

    private static ResponseEntity<ApiError> error(HttpStatusCode status, HttpHeaders headers, String message,
                                                  HttpServletRequest request) {
        return ResponseEntity.status(status).headers(headers).body(new ApiError(OffsetDateTime.now(), status.value(),
                reason(status), message, request.getRequestURI(), RequestIdFilter.currentRequestId()));
    }

    private static String reason(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.getReasonPhrase() : String.valueOf(status.value());
    }
}
