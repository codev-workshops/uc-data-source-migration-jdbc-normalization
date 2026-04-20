package com.workshop.borrowerservice.controller;

import com.workshop.borrowerservice.service.BorrowerService;
import com.workshop.common.dto.BorrowerDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/borrowers")
public class InternalBorrowerController {

    private final BorrowerService borrowerService;

    public InternalBorrowerController(BorrowerService borrowerService) {
        this.borrowerService = borrowerService;
    }

    @GetMapping("/{externalId}")
    public BorrowerDto getBorrowerByExternalId(@PathVariable String externalId) {
        return borrowerService.getBorrowerByExternalId(externalId);
    }
}
