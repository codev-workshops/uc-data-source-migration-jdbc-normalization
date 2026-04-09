package com.workshop.loanservice.entity.modern;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.math.BigDecimal;

/**
 * Modern DynamoDB entity for the Borrowers table.
 * Replaces the legacy CDW_BORR_MSTR table with proper types and clear naming.
 *
 * <p>Table: Borrowers
 * <ul>
 *   <li>Partition Key: borrower_id (S)</li>
 *   <li>GSI StatusIndex: status (PK) + borrower_id (SK)</li>
 *   <li>GSI EmailIndex: email (PK)</li>
 *   <li>GSI LastNameIndex: last_name (PK) + borrower_id (SK)</li>
 * </ul>
 */
@DynamoDbBean
public class Borrower {

    private String borrowerId;
    private String firstName;
    private String lastName;
    private String middleInitial;
    private String ssnHash;
    private String dateOfBirth;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String zipCode;
    private String phone;
    private String email;
    private Integer creditScore;
    private String employmentStatus;
    private BigDecimal annualIncome;
    private String status;
    private String createdAt;
    private String updatedAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("borrower_id")
    @DynamoDbSecondarySortKey(indexNames = {"StatusIndex", "LastNameIndex"})
    public String getBorrowerId() { return borrowerId; }
    public void setBorrowerId(String borrowerId) { this.borrowerId = borrowerId; }

    @DynamoDbAttribute("first_name")
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    @DynamoDbAttribute("last_name")
    @DynamoDbSecondaryPartitionKey(indexNames = "LastNameIndex")
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    @DynamoDbAttribute("middle_initial")
    public String getMiddleInitial() { return middleInitial; }
    public void setMiddleInitial(String middleInitial) { this.middleInitial = middleInitial; }

    @DynamoDbAttribute("ssn_hash")
    public String getSsnHash() { return ssnHash; }
    public void setSsnHash(String ssnHash) { this.ssnHash = ssnHash; }

    @DynamoDbAttribute("date_of_birth")
    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    @DynamoDbAttribute("address_line1")
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    @DynamoDbAttribute("address_line2")
    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    @DynamoDbAttribute("city")
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    @DynamoDbAttribute("state")
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    @DynamoDbAttribute("zip_code")
    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    @DynamoDbAttribute("phone")
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    @DynamoDbAttribute("email")
    @DynamoDbSecondaryPartitionKey(indexNames = "EmailIndex")
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @DynamoDbAttribute("credit_score")
    public Integer getCreditScore() { return creditScore; }
    public void setCreditScore(Integer creditScore) { this.creditScore = creditScore; }

    @DynamoDbAttribute("employment_status")
    public String getEmploymentStatus() { return employmentStatus; }
    public void setEmploymentStatus(String employmentStatus) { this.employmentStatus = employmentStatus; }

    @DynamoDbAttribute("annual_income")
    public BigDecimal getAnnualIncome() { return annualIncome; }
    public void setAnnualIncome(BigDecimal annualIncome) { this.annualIncome = annualIncome; }

    @DynamoDbAttribute("status")
    @DynamoDbSecondaryPartitionKey(indexNames = "StatusIndex")
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @DynamoDbAttribute("created_at")
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @DynamoDbAttribute("updated_at")
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
