package com.workshop.loanservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.dto.ErrorResponse;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void resourceNotFoundMapsTo404() {
    ResponseEntity<ErrorResponse> response =
        handler.handleNotFound(
            new ResourceNotFoundException("Loan", "LN-1"), request("/api/loans/LN-1"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    ErrorResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getStatus()).isEqualTo(404);
    assertThat(body.getError()).isEqualTo("Not Found");
    assertThat(body.getCode()).isEqualTo("RESOURCE_NOT_FOUND");
    assertThat(body.getMessage()).isEqualTo("Loan not found: LN-1");
    assertThat(body.getPath()).isEqualTo("/api/loans/LN-1");
    assertThat(OffsetDateTime.parse(body.getTimestamp())).isNotNull();
  }

  @Test
  void dataAccessExceptionMapsTo500WithDataAccessCode() {
    ResponseEntity<ErrorResponse> response =
        handler.handleDataAccess(
            new DataAccessResourceFailureException("db down"), request("/api/loans"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    ErrorResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getStatus()).isEqualTo(500);
    assertThat(body.getError()).isEqualTo("Internal Server Error");
    assertThat(body.getCode()).isEqualTo("DATA_ACCESS_ERROR");
    assertThat(body.getMessage()).doesNotContain("db down");
    assertThat(body.getPath()).isEqualTo("/api/loans");
  }

  @Test
  void genericExceptionMapsTo500WithInternalErrorCode() {
    ResponseEntity<ErrorResponse> response =
        handler.handleGeneric(new NumberFormatException("For input string: \"abc\""),
            request("/api/borrowers"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    ErrorResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getStatus()).isEqualTo(500);
    assertThat(body.getCode()).isEqualTo("INTERNAL_ERROR");
    assertThat(body.getMessage()).doesNotContain("abc");
    assertThat(body.getPath()).isEqualTo("/api/borrowers");
  }

  private static MockHttpServletRequest request(String uri) {
    return new MockHttpServletRequest("GET", uri);
  }
}
