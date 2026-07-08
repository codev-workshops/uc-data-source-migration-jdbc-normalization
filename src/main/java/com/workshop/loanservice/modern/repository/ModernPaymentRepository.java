package com.workshop.loanservice.modern.repository;

import com.workshop.loanservice.modern.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ModernPaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByLoanAccountId(Long loanAccountId);

    boolean existsByLoanAccountIdAndPaymentDateAndTotalAmountAndType(
            Long loanAccountId, LocalDate paymentDate, BigDecimal totalAmount, String type);
}
