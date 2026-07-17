package com.workshop.loanservice;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.modern.entity.Borrower;
import com.workshop.loanservice.modern.entity.LoanAccount;
import com.workshop.loanservice.modern.entity.LoanProduct;
import com.workshop.loanservice.modern.entity.Payment;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises every getter/setter round-trip on the data-holder classes (legacy
 * and modern entities plus the API DTOs) via reflection. These classes are pure
 * state containers, so a set-then-get equality check is the meaningful unit test
 * for them and keeps their accessors covered without hand-writing hundreds of
 * near-identical assertions.
 */
class PojoAccessorsTest {

    private static final List<Class<?>> POJOS = List.of(
            LegacyBorrower.class,
            LegacyLoanAccount.class,
            LegacyLoanProduct.class,
            LegacyPayment.class,
            Borrower.class,
            LoanAccount.class,
            LoanProduct.class,
            Payment.class,
            BorrowerDto.class,
            LoanSummaryDto.class,
            PaymentDto.class);

    @TestFactory
    List<DynamicTest> accessorsRoundTrip() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (Class<?> type : POJOS) {
            Object instance = type.getDeclaredConstructor().newInstance();
            for (Method setter : type.getDeclaredMethods()) {
                if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1) {
                    continue;
                }
                Class<?> propertyType = setter.getParameterTypes()[0];
                Method getter = findGetter(type, setter.getName().substring(3));
                if (getter == null) {
                    continue;
                }
                Object value = sampleValue(propertyType);
                tests.add(DynamicTest.dynamicTest(type.getSimpleName() + "#" + setter.getName(), () -> {
                    setter.invoke(instance, value);
                    assertEquals(value, getter.invoke(instance));
                }));
            }
        }
        return tests;
    }

    private static Method findGetter(Class<?> type, String suffix) {
        for (String prefix : new String[] {"get", "is"}) {
            try {
                return type.getDeclaredMethod(prefix + suffix);
            } catch (NoSuchMethodException ignored) {
                // try next prefix
            }
        }
        return null;
    }

    private static Object sampleValue(Class<?> type) throws Exception {
        if (type == String.class) return "sample";
        if (type == Long.class || type == long.class) return 1L;
        if (type == Integer.class || type == int.class) return 42;
        if (type == Boolean.class || type == boolean.class) return Boolean.TRUE;
        if (type == BigDecimal.class) return new BigDecimal("1.23");
        if (type == LocalDate.class) return LocalDate.of(2020, 1, 2);
        if (type == LocalDateTime.class) return LocalDateTime.of(2020, 1, 2, 3, 4, 5);
        if (type == List.class) return new ArrayList<>();
        return type.getDeclaredConstructor().newInstance();
    }
}
