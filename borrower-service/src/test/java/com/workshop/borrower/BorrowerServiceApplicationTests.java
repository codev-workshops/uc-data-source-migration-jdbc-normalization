package com.workshop.borrower;

import com.workshop.borrower.dto.BorrowerDto;
import com.workshop.borrower.service.BorrowerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BorrowerServiceApplicationTests {

    @Autowired
    private BorrowerService borrowerService;

    @Test
    void contextLoads() {
    }

    @Test
    void migratedBorrowersHaveProperTypes() {
        List<BorrowerDto> borrowers = borrowerService.getAllBorrowers();
        assertThat(borrowers).hasSize(5);

        BorrowerDto james = borrowerService.getBorrowerById("B-10001");
        assertThat(james.getFullName()).isEqualTo("James R. Mitchell");
        assertThat(james.getCreditScore()).isEqualTo(745);
        assertThat(james.getState()).isEqualTo("IL");
    }
}
