package com.workshop.loanservice.controller;

import com.workshop.loanservice.dto.BorrowerDto;
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
class BorrowerControllerTest {

    @Mock
    private LoanService loanService;

    @InjectMocks
    private BorrowerController borrowerController;

    @Test
    void getAllBorrowers_delegatesToService() {
        BorrowerDto dto = new BorrowerDto();
        dto.setId("B1");
        when(loanService.getAllBorrowers()).thenReturn(List.of(dto));

        List<BorrowerDto> result = borrowerController.getAllBorrowers();

        assertThat(result).containsExactly(dto);
        verify(loanService).getAllBorrowers();
    }

    @Test
    void getBorrower_delegatesToServiceWithId() {
        BorrowerDto dto = new BorrowerDto();
        dto.setId("B1");
        when(loanService.getBorrowerById("B1")).thenReturn(dto);

        BorrowerDto result = borrowerController.getBorrower("B1");

        assertThat(result).isSameAs(dto);
        verify(loanService).getBorrowerById("B1");
    }
}
