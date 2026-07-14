package com.workshop.loan;

import com.workshop.loan.dto.LoanSummaryDto;
import com.workshop.loan.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LoanServiceApplicationTests {

    @Autowired
    private LoanService loanService;

    @Test
    void contextLoads() {
    }

    @Test
    void loansExposeReferencesAndProperTypes() {
        // Peer services aren't running in the test; enrichment falls back to the
        // stored reference ids, but the local typed fields still resolve.
        List<LoanSummaryDto> loans = loanService.getAllLoans();
        assertThat(loans).hasSize(5);

        LoanSummaryDto loan = loanService.getLoanByAccountNumber("LN-2019-00142");
        assertThat(loan.getBorrowerId()).isEqualTo("B-10001");
        assertThat(loan.getProductCode()).isEqualTo("FXD30");
        assertThat(loan.getCurrentBalance()).isEqualByComparingTo("271432.56");
        assertThat(loan.getStatus()).isEqualTo("ACTIVE");
        assertThat(loan.getPropertyAddress()).isEqualTo("742 Elm Street, Springfield, IL 62701");
        assertThat(loan.getPropertyType()).isEqualTo("Single Family Residence");
    }
}
