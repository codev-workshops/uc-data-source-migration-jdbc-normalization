package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.service.LoanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanControllerTest {

    @Mock
    private LoanService loanService;

    @InjectMocks
    private LoanController loanController;

    @Test
    void getAllLoans_delegatesToService() {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber("LN1001");
        when(loanService.getAllLoans()).thenReturn(List.of(dto));

        List<LoanSummaryDto> result = loanController.getAllLoans();

        assertThat(result).containsExactly(dto);
        verify(loanService).getAllLoans();
    }

    @Test
    void getLoan_delegatesToServiceWithId() {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber("LN1001");
        when(loanService.getLoanById("LN1001")).thenReturn(dto);

        LoanSummaryDto result = loanController.getLoan("LN1001");

        assertThat(result).isSameAs(dto);
        verify(loanService).getLoanById("LN1001");
    }

    @Test
    void getPayments_delegatesToServiceWithLoanId() {
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId("P1");
        when(loanService.getPaymentsByLoan("LN1001")).thenReturn(List.of(dto));

        List<PaymentDto> result = loanController.getPayments("LN1001");

        assertThat(result).containsExactly(dto);
        verify(loanService).getPaymentsByLoan("LN1001");
    }
}
