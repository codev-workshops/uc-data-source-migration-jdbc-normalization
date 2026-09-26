package com.workshop.loanservice.dto;

/** Body returned for every error handled by the global exception handler. */
public class ErrorResponse {

  private String timestamp;
  private int status;
  private String error;
  private String code;
  private String message;
  private String path;

  public ErrorResponse() {}

  public ErrorResponse(
      String timestamp, int status, String error, String code, String message, String path) {
    this.timestamp = timestamp;
    this.status = status;
    this.error = error;
    this.code = code;
    this.message = message;
    this.path = path;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }
}
