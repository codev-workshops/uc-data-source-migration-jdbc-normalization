package com.workshop.loanservice.exception;

import com.workshop.loanservice.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates exceptions escaping the controllers into {@link ErrorResponse} bodies. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
  static final String DATA_ACCESS_ERROR = "DATA_ACCESS_ERROR";
  static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(
      ResourceNotFoundException ex, HttpServletRequest request) {
    log.warn("resource not found path={} message={}", request.getRequestURI(), ex.getMessage());
    return build(HttpStatus.NOT_FOUND, RESOURCE_NOT_FOUND, ex.getMessage(), request);
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<ErrorResponse> handleDataAccess(
      DataAccessException ex, HttpServletRequest request) {
    log.error("data access failure path={}", request.getRequestURI(), ex);
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR, DATA_ACCESS_ERROR, "Data access error", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
    log.error("unhandled exception path={}", request.getRequestURI(), ex);
    return build(
        HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR, "Unexpected internal error", request);
  }

  private static ResponseEntity<ErrorResponse> build(
      HttpStatus status, String code, String message, HttpServletRequest request) {
    ErrorResponse body =
        new ErrorResponse(
            OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            status.value(),
            status.getReasonPhrase(),
            code,
            message,
            request.getRequestURI());
    return ResponseEntity.status(status).body(body);
  }
}
