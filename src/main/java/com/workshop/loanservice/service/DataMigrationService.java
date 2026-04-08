package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.MigrationResult;
import com.workshop.loanservice.entity.*;
import com.workshop.loanservice.repository.*;
import com.workshop.loanservice.util.LegacyDataParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataMigrationService {

    private static final Logger logger = LoggerFactory.getLogger(DataMigrationService.class);

    private final LegacyBorrowerRepository legacyBorrowerRepository;
    private final LegacyLoanProductRepository legacyLoanProductRepository;
    private final LegacyLoanAccountRepository legacyLoanAccountRepository;
    private final LegacyPaymentRepository legacyPaymentRepository;
    private final BorrowerRepository borrowerRepository;
    private final ModernLoanProductRepository modernLoanProductRepository;
    private final ModernLoanAccountRepository modernLoanAccountRepository;
    private final ModernPaymentRepository modernPaymentRepository;

    public DataMigrationService(LegacyBorrowerRepository legacyBorrowerRepository,
                                LegacyLoanProductRepository legacyLoanProductRepository,
                                LegacyLoanAccountRepository legacyLoanAccountRepository,
                                LegacyPaymentRepository legacyPaymentRepository,
                                BorrowerRepository borrowerRepository,
                                ModernLoanProductRepository modernLoanProductRepository,
                                ModernLoanAccountRepository modernLoanAccountRepository,
                                ModernPaymentRepository modernPaymentRepository) {
        this.legacyBorrowerRepository = legacyBorrowerRepository;
        this.legacyLoanProductRepository = legacyLoanProductRepository;
        this.legacyLoanAccountRepository = legacyLoanAccountRepository;
        this.legacyPaymentRepository = legacyPaymentRepository;
        this.borrowerRepository = borrowerRepository;
        this.modernLoanProductRepository = modernLoanProductRepository;
        this.modernLoanAccountRepository = modernLoanAccountRepository;
        this.modernPaymentRepository = modernPaymentRepository;
    }

    @Transactional
    public MigrationResult migrate() {
        // Step 1 — Migrate Borrowers
        List<LegacyBorrower> legacyBorrowers = legacyBorrowerRepository.findAll();
        List<Borrower> modernBorrowers = new ArrayList<>();
        for (LegacyBorrower lb : legacyBorrowers) {
            Borrower b = new Borrower();
            b.setExternalId(lb.getBorrowerId());
            b.setFirstName(lb.getFirstName());
            b.setLastName(lb.getLastName());
            b.setMiddleInitial(lb.getMiddleInitial());
            b.setSsnHash(lb.getSsnEncrypted());
            b.setDateOfBirth(LegacyDataParser.parseLegacyDate(lb.getDateOfBirth()));
            b.setAddressLine1(lb.getAddressLine1());
            b.setAddressLine2(lb.getAddressLine2());
            b.setCity(lb.getCity());
            b.setState(lb.getStateCode());
            b.setZipCode(lb.getZipCode());
            b.setPhone(lb.getPhoneNumber());
            b.setEmail(lb.getEmail());
            b.setCreditScore(LegacyDataParser.parseLegacyInteger(lb.getCreditScore()));
            b.setEmploymentStatus(lb.getEmploymentStatus());
            b.setAnnualIncome(LegacyDataParser.parseLegacyAmount(lb.getAnnualIncome()));
            b.setStatus(LegacyDataParser.expandBorrowerStatus(lb.getStatusCode()));
            b.setCreatedAt(LegacyDataParser.parseLegacyTimestamp(lb.getCreatedDate()));
            b.setUpdatedAt(LegacyDataParser.parseLegacyTimestamp(lb.getUpdatedDate()));
            modernBorrowers.add(b);
        }
        modernBorrowers = borrowerRepository.saveAll(modernBorrowers);

        Map<String, Long> borrowerIdMap = new HashMap<>();
        for (Borrower b : modernBorrowers) {
            borrowerIdMap.put(b.getExternalId(), b.getId());
        }
        logger.info("Migrated {} borrowers", modernBorrowers.size());

        // Step 2 — Migrate Loan Products
        List<LegacyLoanProduct> legacyProducts = legacyLoanProductRepository.findAll();
        List<LoanProduct> modernProducts = new ArrayList<>();
        for (LegacyLoanProduct lp : legacyProducts) {
            LoanProduct p = new LoanProduct();
            p.setCode(lp.getProductCode());
            p.setName(lp.getDescription());
            p.setType(lp.getTypeCode());
            p.setTermMonths(LegacyDataParser.parseLegacyInteger(lp.getTermMonths()));
            p.setRateType(lp.getRateType());
            p.setMinAmount(LegacyDataParser.parseLegacyAmount(lp.getMinAmount()));
            p.setMaxAmount(LegacyDataParser.parseLegacyAmount(lp.getMaxAmount()));
            p.setIsActive("ACT".equals(lp.getStatusCode()));
            p.setEffectiveDate(LegacyDataParser.parseLegacyDate(lp.getEffectiveDate()));
            p.setExpirationDate(LegacyDataParser.parseLegacyDate(lp.getExpirationDate()));
            modernProducts.add(p);
        }
        modernProducts = modernLoanProductRepository.saveAll(modernProducts);

        Map<String, Long> productIdMap = new HashMap<>();
        for (LoanProduct p : modernProducts) {
            productIdMap.put(p.getCode(), p.getId());
        }
        logger.info("Migrated {} loan products", modernProducts.size());

        // Step 3 — Migrate Loan Accounts
        List<LegacyLoanAccount> legacyAccounts = legacyLoanAccountRepository.findAll();
        List<LoanAccount> modernAccounts = new ArrayList<>();
        for (LegacyLoanAccount la : legacyAccounts) {
            LoanAccount a = new LoanAccount();
            a.setAccountNumber(la.getLoanAccountNumber());
            a.setBorrower(borrowerRepository.findByExternalId(la.getBorrowerId()).orElseThrow());
            a.setProduct(modernLoanProductRepository.findByCode(la.getProductCode()).orElseThrow());
            a.setOriginalAmount(LegacyDataParser.parseLegacyAmount(la.getOriginalAmount()));
            a.setCurrentBalance(LegacyDataParser.parseLegacyAmount(la.getCurrentBalance()));
            a.setInterestRate(LegacyDataParser.parseLegacyDecimal(la.getInterestRate()));
            a.setTermMonths(LegacyDataParser.parseLegacyInteger(la.getTermMonths()));
            a.setMonthlyPayment(LegacyDataParser.parseLegacyAmount(la.getMonthlyPayment()));
            a.setOriginationDate(LegacyDataParser.parseLegacyDate(la.getOriginationDate()));
            a.setMaturityDate(LegacyDataParser.parseLegacyDate(la.getMaturityDate()));
            a.setFirstPaymentDate(LegacyDataParser.parseLegacyDate(la.getFirstPaymentDate()));
            a.setNextPaymentDate(LegacyDataParser.parseLegacyDate(la.getNextPaymentDate()));
            a.setStatus(LegacyDataParser.expandLoanStatus(la.getStatusCode()));
            a.setDelinquencyDays(LegacyDataParser.parseLegacyInteger(la.getDelinquencyDays()));
            a.setEscrowBalance(LegacyDataParser.parseLegacyAmount(la.getEscrowBalance()));
            a.setLtvPercent(LegacyDataParser.parseLegacyDecimal(la.getLtvPercent()));
            a.setPropertyAddress(la.getPropertyAddress());
            a.setPropertyCity(la.getPropertyCity());
            a.setPropertyState(la.getPropertyState());
            a.setPropertyZip(la.getPropertyZip());
            a.setPropertyType(LegacyDataParser.expandPropertyType(la.getPropertyType()));
            a.setAppraisedValue(LegacyDataParser.parseLegacyAmount(la.getAppraisedValue()));
            a.setCreatedAt(LegacyDataParser.parseLegacyTimestamp(la.getCreatedDate()));
            a.setUpdatedAt(LegacyDataParser.parseLegacyTimestamp(la.getUpdatedDate()));
            modernAccounts.add(a);
        }
        modernAccounts = modernLoanAccountRepository.saveAll(modernAccounts);

        Map<String, Long> accountIdMap = new HashMap<>();
        for (LoanAccount a : modernAccounts) {
            accountIdMap.put(a.getAccountNumber(), a.getId());
        }
        logger.info("Migrated {} loan accounts", modernAccounts.size());

        // Step 4 — Migrate Payments
        List<LegacyPayment> legacyPayments = legacyPaymentRepository.findAll();
        List<Payment> modernPayments = new ArrayList<>();
        for (LegacyPayment lp : legacyPayments) {
            Payment p = new Payment();
            p.setLoanAccount(modernLoanAccountRepository.findByAccountNumber(lp.getLoanAccountNumber()).orElseThrow());
            p.setPaymentDate(LegacyDataParser.parseLegacyDate(lp.getPaymentDate()));
            p.setTotalAmount(LegacyDataParser.parseLegacyAmount(lp.getTotalAmount()));
            p.setPrincipalAmount(LegacyDataParser.parseLegacyAmount(lp.getPrincipalAmount()));
            p.setInterestAmount(LegacyDataParser.parseLegacyAmount(lp.getInterestAmount()));
            p.setEscrowAmount(LegacyDataParser.parseLegacyAmount(lp.getEscrowAmount()));
            p.setLateFee(LegacyDataParser.parseLegacyAmount(lp.getLateFee()));
            p.setType(LegacyDataParser.expandPaymentType(lp.getTypeCode()));
            p.setStatus(LegacyDataParser.expandPaymentStatus(lp.getStatusCode()));
            p.setReceivedDate(LegacyDataParser.parseLegacyDate(lp.getReceivedDate()));
            p.setProcessedDate(LegacyDataParser.parseLegacyDate(lp.getProcessedDate()));
            p.setCreatedAt(LegacyDataParser.parseLegacyTimestamp(lp.getCreatedDate()));
            p.setUpdatedAt(LegacyDataParser.parseLegacyTimestamp(lp.getUpdatedDate()));
            modernPayments.add(p);
        }
        modernPayments = modernPaymentRepository.saveAll(modernPayments);
        logger.info("Migrated {} payments", modernPayments.size());

        return new MigrationResult(
                modernBorrowers.size(),
                modernProducts.size(),
                modernAccounts.size(),
                modernPayments.size()
        );
    }
}
