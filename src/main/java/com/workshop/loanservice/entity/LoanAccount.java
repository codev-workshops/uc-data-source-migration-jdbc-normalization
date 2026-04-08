package com.workshop.loanservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "loan_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", unique = true, nullable = false)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Borrower borrower;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LoanProduct product;

    @Column(name = "original_amount", nullable = false)
    private BigDecimal originalAmount;

    @Column(name = "current_balance", nullable = false)
    private BigDecimal currentBalance;

    @Column(name = "interest_rate", nullable = false)
    private BigDecimal interestRate;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "monthly_payment", nullable = false)
    private BigDecimal monthlyPayment;

    @Column(name = "origination_date", nullable = false)
    private LocalDate originationDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Column(name = "first_payment_date")
    private LocalDate firstPaymentDate;

    @Column(name = "next_payment_date")
    private LocalDate nextPaymentDate;

    private String status;

    @Column(name = "delinquency_days")
    private Integer delinquencyDays;

    @Column(name = "escrow_balance")
    private BigDecimal escrowBalance;

    @Column(name = "ltv_percent")
    private BigDecimal ltvPercent;

    @Column(name = "property_address")
    private String propertyAddress;

    @Column(name = "property_city")
    private String propertyCity;

    @Column(name = "property_state")
    private String propertyState;

    @Column(name = "property_zip")
    private String propertyZip;

    @Column(name = "property_type")
    private String propertyType;

    @Column(name = "appraised_value")
    private BigDecimal appraisedValue;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "loanAccount", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Payment> payments;
}
