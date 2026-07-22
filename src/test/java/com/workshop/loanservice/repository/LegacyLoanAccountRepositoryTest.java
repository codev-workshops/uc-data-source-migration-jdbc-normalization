package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LegacyLoanAccountRepositoryTest {

    @Autowired
    private LegacyLoanAccountRepository repository;

    @Test
    void findByBorrowerId_returnsMatchingAccounts() {
        List<LegacyLoanAccount> accounts = repository.findByBorrowerId("B-10001");

        assertThat(accounts).extracting(LegacyLoanAccount::getLoanAccountNumber)
                .containsExactly("LN-2019-00142");
    }

    @Test
    void findByStatusCode_returnsAllActiveLoans() {
        List<LegacyLoanAccount> accounts = repository.findByStatusCode("ACT");

        assertThat(accounts).hasSize(5);
        assertThat(accounts).allMatch(a -> "ACT".equals(a.getStatusCode()));
    }

    @Test
    void findByProductCode_returnsMatchingAccounts() {
        List<LegacyLoanAccount> accounts = repository.findByProductCode("FXD30");

        assertThat(accounts).extracting(LegacyLoanAccount::getLoanAccountNumber)
                .containsExactlyInAnyOrder("LN-2019-00142", "LN-2021-00567");
    }

    @Test
    void findByProductCode_unknownCode_returnsEmpty() {
        assertThat(repository.findByProductCode("NOPE")).isEmpty();
    }
}
