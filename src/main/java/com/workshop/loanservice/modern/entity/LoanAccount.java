package com.workshop.loanservice.modern.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps to the modern {@code loan_accounts} table.
 */
@Entity
@Table(name = "loan_accounts")
public class LoanAccount extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "borrower_id", nullable = false)
    private Borrower borrower;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private LoanProduct product;

    @Column(name = "original_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "current_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 3)
    private BigDecimal interestRate;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "monthly_payment", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyPayment;

    @Column(name = "origination_date", nullable = false)
    private LocalDate originationDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "first_payment_date")
    private LocalDate firstPaymentDate;

    @Column(name = "next_payment_date")
    private LocalDate nextPaymentDate;

    /** ACTIVE, CLOSED, DEFAULT, FORBEARANCE */
    @Column(name = "status", length = 15)
    @ColumnDefault("'ACTIVE'")
    private String status = "ACTIVE";

    @Column(name = "delinquency_days")
    @ColumnDefault("0")
    private Integer delinquencyDays = 0;

    @Column(name = "escrow_balance", precision = 10, scale = 2)
    @ColumnDefault("0")
    private BigDecimal escrowBalance = BigDecimal.ZERO;

    @Column(name = "ltv_percent", precision = 5, scale = 2)
    private BigDecimal ltvPercent;

    @Column(name = "property_address", length = 100)
    private String propertyAddress;

    @Column(name = "property_city", length = 50)
    private String propertyCity;

    @Column(name = "property_state", length = 2)
    private String propertyState;

    @Column(name = "property_zip", length = 10)
    private String propertyZip;

    @Column(name = "property_type", length = 30)
    private String propertyType;

    @Column(name = "appraised_value", precision = 12, scale = 2)
    private BigDecimal appraisedValue;

    @OneToMany(mappedBy = "loanAccount")
    private List<Payment> payments = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public Borrower getBorrower() { return borrower; }
    public void setBorrower(Borrower borrower) { this.borrower = borrower; }
    public LoanProduct getProduct() { return product; }
    public void setProduct(LoanProduct product) { this.product = product; }
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public Integer getTermMonths() { return termMonths; }
    public void setTermMonths(Integer termMonths) { this.termMonths = termMonths; }
    public BigDecimal getMonthlyPayment() { return monthlyPayment; }
    public void setMonthlyPayment(BigDecimal monthlyPayment) { this.monthlyPayment = monthlyPayment; }
    public LocalDate getOriginationDate() { return originationDate; }
    public void setOriginationDate(LocalDate originationDate) { this.originationDate = originationDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
    public LocalDate getFirstPaymentDate() { return firstPaymentDate; }
    public void setFirstPaymentDate(LocalDate firstPaymentDate) { this.firstPaymentDate = firstPaymentDate; }
    public LocalDate getNextPaymentDate() { return nextPaymentDate; }
    public void setNextPaymentDate(LocalDate nextPaymentDate) { this.nextPaymentDate = nextPaymentDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getDelinquencyDays() { return delinquencyDays; }
    public void setDelinquencyDays(Integer delinquencyDays) { this.delinquencyDays = delinquencyDays; }
    public BigDecimal getEscrowBalance() { return escrowBalance; }
    public void setEscrowBalance(BigDecimal escrowBalance) { this.escrowBalance = escrowBalance; }
    public BigDecimal getLtvPercent() { return ltvPercent; }
    public void setLtvPercent(BigDecimal ltvPercent) { this.ltvPercent = ltvPercent; }
    public String getPropertyAddress() { return propertyAddress; }
    public void setPropertyAddress(String propertyAddress) { this.propertyAddress = propertyAddress; }
    public String getPropertyCity() { return propertyCity; }
    public void setPropertyCity(String propertyCity) { this.propertyCity = propertyCity; }
    public String getPropertyState() { return propertyState; }
    public void setPropertyState(String propertyState) { this.propertyState = propertyState; }
    public String getPropertyZip() { return propertyZip; }
    public void setPropertyZip(String propertyZip) { this.propertyZip = propertyZip; }
    public String getPropertyType() { return propertyType; }
    public void setPropertyType(String propertyType) { this.propertyType = propertyType; }
    public BigDecimal getAppraisedValue() { return appraisedValue; }
    public void setAppraisedValue(BigDecimal appraisedValue) { this.appraisedValue = appraisedValue; }
    public List<Payment> getPayments() { return payments; }
    public void setPayments(List<Payment> payments) { this.payments = payments; }
}
