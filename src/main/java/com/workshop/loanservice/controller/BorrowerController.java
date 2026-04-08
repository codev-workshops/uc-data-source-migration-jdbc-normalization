package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.service.LoanService;
import com.workshop.loanservice.service.ModernLoanService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/borrowers")
public class BorrowerController {

    private final LoanService loanService;
    private final ModernLoanService modernLoanService;
    private final boolean useModern;

    public BorrowerController(LoanService loanService,
                               ModernLoanService modernLoanService,
                               @Value("${app.use-modern-datasource:false}") boolean useModern) {
        this.loanService = loanService;
        this.modernLoanService = modernLoanService;
        this.useModern = useModern;
    }

    @GetMapping
    public List<BorrowerDto> getAllBorrowers() {
        return useModern ? modernLoanService.getAllBorrowers() : loanService.getAllBorrowers();
    }

    @GetMapping("/{id}")
    public BorrowerDto getBorrower(@PathVariable String id) {
        return useModern ? modernLoanService.getBorrowerById(id) : loanService.getBorrowerById(id);
    }
}
