package com.workshop.loanservice.controller;

import com.workshop.common.dto.LoanSummaryDto;
import com.workshop.loanservice.service.LoanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/loans")
public class InternalLoanController {

    private final LoanService loanService;

    public InternalLoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping("/by-borrower/{borrowerId}")
    public List<LoanSummaryDto> getLoansByBorrowerId(@PathVariable String borrowerId) {
        return loanService.getLoansByBorrowerId(borrowerId);
    }
}
