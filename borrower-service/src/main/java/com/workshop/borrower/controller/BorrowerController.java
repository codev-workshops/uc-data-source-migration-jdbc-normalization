package com.workshop.borrower.controller;

import com.workshop.borrower.client.LoanClient;
import com.workshop.borrower.dto.BorrowerDto;
import com.workshop.borrower.dto.BorrowerLoanDto;
import com.workshop.borrower.service.BorrowerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/borrowers")
public class BorrowerController {

    private final BorrowerService borrowerService;
    private final LoanClient loanClient;

    public BorrowerController(BorrowerService borrowerService, LoanClient loanClient) {
        this.borrowerService = borrowerService;
        this.loanClient = loanClient;
    }

    @GetMapping
    public List<BorrowerDto> getAllBorrowers() {
        return borrowerService.getAllBorrowers();
    }

    @GetMapping("/{id}")
    public BorrowerDto getBorrower(@PathVariable String id) {
        return borrowerService.getBorrowerById(id);
    }

    /**
     * Loans for a borrower, resolved by calling loan-service rather than joining
     * a local table (loans live in a different bounded context).
     */
    @GetMapping("/{id}/loans")
    public List<BorrowerLoanDto> getBorrowerLoans(@PathVariable String id) {
        borrowerService.getBorrowerById(id); // validate borrower exists
        return loanClient.getLoansForBorrower(id);
    }
}
