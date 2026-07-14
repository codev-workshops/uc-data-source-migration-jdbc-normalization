package com.workshop.borrower.service;

import com.workshop.borrower.dto.BorrowerDto;
import com.workshop.borrower.entity.Borrower;
import com.workshop.borrower.repository.BorrowerRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class BorrowerService {

    private final BorrowerRepository borrowerRepository;

    public BorrowerService(BorrowerRepository borrowerRepository) {
        this.borrowerRepository = borrowerRepository;
    }

    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public BorrowerDto getBorrowerById(String externalId) {
        Borrower borrower = borrowerRepository.findByExternalId(externalId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Borrower not found: " + externalId));
        return toDto(borrower);
    }

    private BorrowerDto toDto(Borrower borrower) {
        BorrowerDto dto = new BorrowerDto();
        dto.setId(borrower.getExternalId());
        String middle = borrower.getMiddleInitial() != null ? " " + borrower.getMiddleInitial() + "." : "";
        dto.setFullName(borrower.getFirstName() + middle + " " + borrower.getLastName());
        dto.setEmail(borrower.getEmail());
        dto.setPhone(borrower.getPhone());
        dto.setCity(borrower.getCity());
        dto.setState(borrower.getState());
        dto.setCreditScore(borrower.getCreditScore());
        dto.setEmploymentStatus(borrower.getEmploymentStatus());
        return dto;
    }
}
