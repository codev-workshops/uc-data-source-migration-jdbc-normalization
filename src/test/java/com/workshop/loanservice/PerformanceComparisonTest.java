package com.workshop.loanservice;

import com.workshop.loanservice.service.LegacyLoanDataProvider;
import com.workshop.loanservice.service.LoanDataProvider;
import com.workshop.loanservice.service.ModernLoanDataProvider;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lightweight benchmark comparing read throughput of the legacy
 * VARCHAR-everything schema (which parses strings on every read) against the
 * properly-typed modern schema. Timings are logged (not asserted) so the test
 * stays deterministic; it asserts only functional parity between the two paths.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PerformanceComparisonTest {

    private static final Logger log = LoggerFactory.getLogger(PerformanceComparisonTest.class);

    private static final int WARMUP = 1_000;
    private static final int ITERATIONS = 10_000;

    @Autowired ModernLoanDataProvider modern;
    @Autowired LegacyLoanDataProvider legacy;

    @Test
    void compareReadPerformance() {
        // functional parity guard so this is a meaningful test, not just timing
        assertThat(modern.getAllLoans()).hasSize(legacy.getAllLoans().size());
        assertThat(modern.getBorrowerById("B-10001").getFullName())
                .isEqualTo(legacy.getBorrowerById("B-10001").getFullName());

        benchmark("getAllLoans", i -> legacy.getAllLoans().size(), i -> modern.getAllLoans().size());
        benchmark("getBorrowerById",
                i -> legacy.getBorrowerById("B-10001").getLoans().size(),
                i -> modern.getBorrowerById("B-10001").getLoans().size());
        benchmark("getPaymentsByLoan",
                i -> legacy.getPaymentsByLoan("LN-2019-00142").size(),
                i -> modern.getPaymentsByLoan("LN-2019-00142").size());
    }

    private void benchmark(String label, IntConsumer legacyOp, IntConsumer modernOp) {
        for (int i = 0; i < WARMUP; i++) {
            legacyOp.accept(i);
            modernOp.accept(i);
        }
        long legacyNanos = time(legacyOp);
        long modernNanos = time(modernOp);
        double legacyUs = legacyNanos / 1_000.0 / ITERATIONS;
        double modernUs = modernNanos / 1_000.0 / ITERATIONS;
        log.info("BENCHMARK {} ({} iters): legacy={} us/op, modern={} us/op, speedup={}x",
                label, ITERATIONS,
                String.format("%.2f", legacyUs),
                String.format("%.2f", modernUs),
                String.format("%.2f", legacyUs / modernUs));
    }

    private long time(IntConsumer op) {
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            op.accept(i);
        }
        return System.nanoTime() - start;
    }
}
