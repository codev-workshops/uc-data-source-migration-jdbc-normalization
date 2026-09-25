package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.FieldViolationDto;
import com.workshop.loanservice.service.validation.InvalidInputException;
import com.workshop.loanservice.service.validation.RecordNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps input failures to 400 (with violations) and unknown keys to 404. */
@RestControllerAdvice
public class InvalidInputAdvice {

  @ExceptionHandler(InvalidInputException.class)
  public ResponseEntity<Map<String, Object>> handle(InvalidInputException ex) {
    List<FieldViolationDto> violations =
        ex.getViolations().stream().map(FieldViolationDto::from).toList();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", "Invalid input", "dataQualityViolations", violations));
  }

  @ExceptionHandler(RecordNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handle(RecordNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
  }
}
