package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.service.ModernLoanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/borrowers")
public class BorrowerController {

    private final ModernLoanService modernLoanService;

    public BorrowerController(ModernLoanService modernLoanService) {
        this.modernLoanService = modernLoanService;
    }

    @GetMapping
    public List<BorrowerDto> getAllBorrowers() {
        return modernLoanService.getAllBorrowers();
    }

    @GetMapping("/{id}")
    public BorrowerDto getBorrower(@PathVariable String id) {
        return modernLoanService.getBorrowerById(id);
    }
}
