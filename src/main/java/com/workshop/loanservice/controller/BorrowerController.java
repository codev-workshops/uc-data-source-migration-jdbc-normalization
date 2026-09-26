package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.service.LoanQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/borrowers")
public class BorrowerController {

    private static final Logger log = LoggerFactory.getLogger(BorrowerController.class);

    private final LoanQueryService loanService;

    public BorrowerController(LoanQueryService loanService) {
        this.loanService = loanService;
    }

    @GetMapping
    public List<BorrowerDto> getAllBorrowers() {
        log.info("GET /api/borrowers");
        return loanService.getAllBorrowers();
    }

    @GetMapping("/{id}")
    public BorrowerDto getBorrower(@PathVariable String id) {
        log.info("GET /api/borrowers/{}", id);
        return loanService.getBorrowerById(id);
    }
}
