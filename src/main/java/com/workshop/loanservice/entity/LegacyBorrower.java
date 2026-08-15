package com.workshop.loanservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps to the legacy CDW_BORR_MSTR table.
 * All fields are strings (legacy DW pattern — loose typing).
 */
/**
 * @deprecated No longer used by production request paths: {@code LoanService}
 * now reads from the modern normalized schema. Retained because
 * {@code DataMigrationService}, {@code LegacyDtoAssembler} /
 * {@code ValidationController} and any rollback still read the legacy CDW
 * source of record. Do not delete.
 */
@Deprecated
@Entity
@Table(name = "CDW_BORR_MSTR")
public class LegacyBorrower {

    @Id
    @Column(name = "BORR_ID")
    private String borrowerId;

    @Column(name = "BORR_FST_NM")
    private String firstName;

    @Column(name = "BORR_LST_NM")
    private String lastName;

    @Column(name = "BORR_MID_INIT")
    private String middleInitial;

    @Column(name = "BORR_SSN_ENCR")
    private String ssnEncrypted;

    @Column(name = "BORR_DOB_DT")
    private String dateOfBirth;

    @Column(name = "BORR_ADDR_LN1")
    private String addressLine1;

    @Column(name = "BORR_ADDR_LN2")
    private String addressLine2;

    @Column(name = "BORR_CTY_NM")
    private String city;

    @Column(name = "BORR_ST_CD")
    private String stateCode;

    @Column(name = "BORR_ZIP_CD")
    private String zipCode;

    @Column(name = "BORR_PH_NBR")
    private String phoneNumber;

    @Column(name = "BORR_EMAIL_ADDR")
    private String email;

    @Column(name = "BORR_CRDT_SCR")
    private String creditScore;

    @Column(name = "BORR_EMP_STAT")
    private String employmentStatus;

    @Column(name = "BORR_ANN_INCM")
    private String annualIncome;

    @Column(name = "BORR_CRET_DT")
    private String createdDate;

    @Column(name = "BORR_UPDT_DT")
    private String updatedDate;

    @Column(name = "BORR_STAT_CD")
    private String statusCode;

    @Column(name = "BORR_REC_TYP")
    private String recordType;

    public String getBorrowerId() { return borrowerId; }
    public void setBorrowerId(String borrowerId) { this.borrowerId = borrowerId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getMiddleInitial() { return middleInitial; }
    public void setMiddleInitial(String middleInitial) { this.middleInitial = middleInitial; }
    public String getSsnEncrypted() { return ssnEncrypted; }
    public void setSsnEncrypted(String ssnEncrypted) { this.ssnEncrypted = ssnEncrypted; }
    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCreditScore() { return creditScore; }
    public void setCreditScore(String creditScore) { this.creditScore = creditScore; }
    public String getEmploymentStatus() { return employmentStatus; }
    public void setEmploymentStatus(String employmentStatus) { this.employmentStatus = employmentStatus; }
    public String getAnnualIncome() { return annualIncome; }
    public void setAnnualIncome(String annualIncome) { this.annualIncome = annualIncome; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(String updatedDate) { this.updatedDate = updatedDate; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public String getRecordType() { return recordType; }
    public void setRecordType(String recordType) { this.recordType = recordType; }
}
