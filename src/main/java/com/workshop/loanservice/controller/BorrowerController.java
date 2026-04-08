package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.service.LoanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/borrowers")
public class BorrowerController {

    private final LoanService loanService;

    public BorrowerController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping
    public List<BorrowerDto> getAllBorrowers() {
        return loanService.getAllBorrowers();
    }

    @GetMapping("/{id}")
    public BorrowerDto getBorrower(@PathVariable String id) {
        return loanService.getBorrowerById(id);
    }
}
