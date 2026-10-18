package com.workshop.loanservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.workshop.loanservice.service.LoanService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LoanServiceApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void loanServiceContainsNoLegacyParsingOrCodeExpansion() {
        assertThat(Arrays.stream(LoanService.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("parse") || name.startsWith("expand"));
    }

    @Test
    void loanServiceDependsOnlyOnModernRepositories() {
        assertThat(Arrays.stream(LoanService.class.getDeclaredFields()).map(Field::getType))
                .allSatisfy(type -> assertThat(type.getPackageName())
                        .doesNotContain(".legacy"));
    }
}
