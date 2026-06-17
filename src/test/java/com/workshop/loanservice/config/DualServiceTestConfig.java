package com.workshop.loanservice.config;

import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LoanProductRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import com.workshop.loanservice.service.LegacyLoanServiceImpl;
import com.workshop.loanservice.service.LoanServiceInterface;
import com.workshop.loanservice.service.ModernLoanServiceImpl;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class DualServiceTestConfig {

    @Bean("legacyService")
    @Primary
    public LoanServiceInterface legacyService(
            LegacyBorrowerRepository legacyBorrowerRepo,
            LegacyLoanAccountRepository legacyLoanAccountRepo,
            LegacyLoanProductRepository legacyProductRepo,
            LegacyPaymentRepository legacyPaymentRepo) {
        return new LegacyLoanServiceImpl(legacyBorrowerRepo, legacyLoanAccountRepo, legacyProductRepo, legacyPaymentRepo);
    }

    @Bean("modernService")
    public LoanServiceInterface modernService(
            BorrowerRepository borrowerRepo,
            LoanAccountRepository loanAccountRepo,
            LoanProductRepository productRepo,
            PaymentRepository paymentRepo) {
        return new ModernLoanServiceImpl(borrowerRepo, loanAccountRepo, productRepo, paymentRepo);
    }
}
