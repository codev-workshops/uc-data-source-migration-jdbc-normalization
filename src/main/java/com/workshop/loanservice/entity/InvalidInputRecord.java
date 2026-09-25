package com.workshop.loanservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One quarantined violation in {@code DQ_INVALID_INPUT}, written by API validation mode. */
@Entity
@Table(name = "DQ_INVALID_INPUT")
public class InvalidInputRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ID")
  private Long id;

  @Column(name = "RECORDED_AT", nullable = false)
  private Instant recordedAt;

  @Column(name = "ENDPOINT", nullable = false, length = 100)
  private String endpoint;

  @Column(name = "INPUT_NAME", length = 50)
  private String inputName;

  @Column(name = "INPUT_VALUE", length = 200)
  private String inputValue;

  @Column(name = "ENTITY_TYPE", nullable = false, length = 30)
  private String entityType;

  @Column(name = "RECORD_KEY", length = 50)
  private String recordKey;

  @Column(name = "FIELD", nullable = false, length = 50)
  private String field;

  @Column(name = "RAW_VALUE", length = 500)
  private String rawValue;

  @Column(name = "RULE_ID", nullable = false, length = 40)
  private String ruleId;

  @Column(name = "SEVERITY", nullable = false, length = 5)
  private String severity;

  @Column(name = "MESSAGE", nullable = false, length = 500)
  private String message;

  public Long getId() {
    return id;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public void setRecordedAt(Instant recordedAt) {
    this.recordedAt = recordedAt;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getInputName() {
    return inputName;
  }

  public void setInputName(String inputName) {
    this.inputName = inputName;
  }

  public String getInputValue() {
    return inputValue;
  }

  public void setInputValue(String inputValue) {
    this.inputValue = inputValue;
  }

  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String entityType) {
    this.entityType = entityType;
  }

  public String getRecordKey() {
    return recordKey;
  }

  public void setRecordKey(String recordKey) {
    this.recordKey = recordKey;
  }

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  public String getRawValue() {
    return rawValue;
  }

  public void setRawValue(String rawValue) {
    this.rawValue = rawValue;
  }

  public String getRuleId() {
    return ruleId;
  }

  public void setRuleId(String ruleId) {
    this.ruleId = ruleId;
  }

  public String getSeverity() {
    return severity;
  }

  public void setSeverity(String severity) {
    this.severity = severity;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
