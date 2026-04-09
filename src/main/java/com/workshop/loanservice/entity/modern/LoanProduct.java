package com.workshop.loanservice.entity.modern;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.math.BigDecimal;

/**
 * Modern DynamoDB entity for the LoanProducts table.
 * Replaces the legacy CDW_LN_PROD table.
 *
 * <p>Table: LoanProducts
 * <ul>
 *   <li>Partition Key: product_code (S)</li>
 * </ul>
 */
@DynamoDbBean
public class LoanProduct {

    private String productCode;
    private String name;
    private String type;
    private Integer termMonths;
    private String rateType;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Boolean isActive;
    private String effectiveDate;
    private String expirationDate;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("product_code")
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    @DynamoDbAttribute("name")
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @DynamoDbAttribute("type")
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @DynamoDbAttribute("term_months")
    public Integer getTermMonths() { return termMonths; }
    public void setTermMonths(Integer termMonths) { this.termMonths = termMonths; }

    @DynamoDbAttribute("rate_type")
    public String getRateType() { return rateType; }
    public void setRateType(String rateType) { this.rateType = rateType; }

    @DynamoDbAttribute("min_amount")
    public BigDecimal getMinAmount() { return minAmount; }
    public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }

    @DynamoDbAttribute("max_amount")
    public BigDecimal getMaxAmount() { return maxAmount; }
    public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }

    @DynamoDbAttribute("is_active")
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    @DynamoDbAttribute("effective_date")
    public String getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(String effectiveDate) { this.effectiveDate = effectiveDate; }

    @DynamoDbAttribute("expiration_date")
    public String getExpirationDate() { return expirationDate; }
    public void setExpirationDate(String expirationDate) { this.expirationDate = expirationDate; }
}
