package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Legacy row rejected by the migration loader, kept verbatim for manual review. */
@Entity
@Table(name = "migration_quarantine")
public class MigrationQuarantine {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "source_table", nullable = false, length = 30)
  private String sourceTable;

  @Column(name = "source_key", nullable = false, length = 40)
  private String sourceKey;

  @Column(name = "source_row", nullable = false, length = 4000)
  private String sourceRow;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason_code", nullable = false, length = 30)
  private QuarantineReason reasonCode;

  @Column(name = "field", length = 30)
  private String field;

  @Column(name = "detail", length = 500)
  private String detail;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected MigrationQuarantine() {}

  public MigrationQuarantine(
      String sourceTable,
      String sourceKey,
      String sourceRow,
      QuarantineReason reasonCode,
      String field,
      String detail) {
    this.sourceTable = sourceTable;
    this.sourceKey = sourceKey;
    this.sourceRow = sourceRow;
    this.reasonCode = reasonCode;
    this.field = field;
    this.detail = detail;
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }

  public Long getId() {
    return id;
  }

  public String getSourceTable() {
    return sourceTable;
  }

  public String getSourceKey() {
    return sourceKey;
  }

  public String getSourceRow() {
    return sourceRow;
  }

  public QuarantineReason getReasonCode() {
    return reasonCode;
  }

  public String getField() {
    return field;
  }

  public String getDetail() {
    return detail;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
