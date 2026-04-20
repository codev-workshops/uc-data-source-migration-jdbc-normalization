package com.workshop.borrowerservice.service;

import com.workshop.borrowerservice.client.LoanServiceClient;
import com.workshop.borrowerservice.entity.LegacyBorrower;
import com.workshop.borrowerservice.repository.LegacyBorrowerRepository;
import com.workshop.common.dto.BorrowerDto;
import com.workshop.common.dto.LoanSummaryDto;
import com.workshop.common.exception.ResourceNotFoundException;
import com.workshop.common.util.LegacyDataParser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BorrowerService {

    private final LegacyBorrowerRepository borrowerRepository;
    private final LoanServiceClient loanServiceClient;

    public BorrowerService(LegacyBorrowerRepository borrowerRepository,
                           LoanServiceClient loanServiceClient) {
        this.borrowerRepository = borrowerRepository;
        this.loanServiceClient = loanServiceClient;
    }

    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAll().stream()
                .map(this::toBorrowerDto)
                .collect(Collectors.toList());
    }

    public BorrowerDto getBorrowerById(String borrowerId) {
        LegacyBorrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new ResourceNotFoundException("Borrower not found: " + borrowerId));
        BorrowerDto dto = toBorrowerDto(borrower);

        List<LoanSummaryDto> loans = loanServiceClient.getLoansByBorrowerId(borrowerId);
        dto.setLoans(loans);

        return dto;
    }

    public BorrowerDto getBorrowerByExternalId(String externalId) {
        LegacyBorrower borrower = borrowerRepository.findById(externalId)
                .orElseThrow(() -> new ResourceNotFoundException("Borrower not found: " + externalId));
        return toBorrowerDto(borrower);
    }

    private BorrowerDto toBorrowerDto(LegacyBorrower borrower) {
        BorrowerDto dto = new BorrowerDto();
        dto.setId(borrower.getBorrowerId());
        String middle = borrower.getMiddleInitial() != null ? " " + borrower.getMiddleInitial() + "." : "";
        dto.setFullName(borrower.getFirstName() + middle + " " + borrower.getLastName());
        dto.setEmail(borrower.getEmail());
        dto.setPhone(borrower.getPhoneNumber());
        dto.setCity(borrower.getCity());
        dto.setState(borrower.getStateCode());
        dto.setCreditScore(LegacyDataParser.parseLegacyInteger(borrower.getCreditScore()));
        dto.setEmploymentStatus(borrower.getEmploymentStatus());
        return dto;
    }
}
