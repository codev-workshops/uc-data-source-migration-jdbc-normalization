package com.workshop.loanservice.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workshop.loanservice.entity.modern.Borrower;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ModernEntityInspectorTest {

    private static final String SECRET = "x".repeat(150);

    @Test
    void oversizedTextIsRejectedWithoutEchoingTheValue() {
        Borrower borrower = new Borrower();
        borrower.setSsnHash(SECRET);

        assertThatThrownBy(() -> ModernEntityInspector.validateColumnConstraints(borrower))
                .isInstanceOf(LegacyDataException.class)
                .hasMessageContaining("ssn_hash")
                .hasMessageNotContaining(SECRET);
    }

    @Test
    void differencesListFieldNamesWithoutValues() {
        Borrower legacy = new Borrower();
        legacy.setSsnHash("hash-a");
        legacy.setDateOfBirth(LocalDate.of(1980, 1, 1));
        legacy.setFirstName("James");
        legacy.setAnnualIncome(new BigDecimal("92500"));
        legacy.setCreditScore(745);
        Borrower modern = new Borrower();
        modern.setSsnHash("hash-b");
        modern.setDateOfBirth(LocalDate.of(1981, 1, 1));
        modern.setFirstName("Jon");
        modern.setAnnualIncome(new BigDecimal("1.00"));
        modern.setCreditScore(300);

        String report = String.join(", ", ModernEntityInspector.differingFields(legacy, modern));

        assertThat(report).isEqualTo("firstName, ssnHash, dateOfBirth, creditScore, annualIncome");
    }
}
