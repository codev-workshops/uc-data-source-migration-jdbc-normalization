package com.workshop.loanservice.performance;

import com.workshop.loanservice.service.LegacyLoanDataProvider;
import com.workshop.loanservice.service.LoanDataProvider;
import com.workshop.loanservice.service.ModernLoanDataProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Legacy-vs-modern performance comparison (bonus task). Benchmarks the *same*
 * read operations against the legacy VARCHAR-everything provider (which parses
 * strings → dates/decimals/ints on every read) and the modern typed provider
 * (which reads native types), over the identical 5/5/5/10 data set migrated by
 * the real ETL.
 *
 * <p>This is a comparative micro-benchmark, not an absolute one: both providers
 * hit the same in-memory H2, so the DB round-trip is a shared constant and the
 * delta isolates the string-parsing / type-conversion cost the migration
 * removes. It is a more controlled measure than timing whole test cases (which
 * are dominated by Spring/MockMvc overhead): it warms up the JIT, then times
 * many iterations of each operation.
 *
 * <p>Tagged {@code performance} so it is excluded from the normal build; run it
 * with {@code mvn test -Pperformance}. Results are printed and written to
 * {@code target/performance-comparison.md}.
 */
@SpringBootTest
@Tag("performance")
@TestPropertySource(properties = {
        // SQL console logging would dwarf the actual work and make timings noise.
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=WARN"
})
class PerformanceComparisonTest {

    private static final int WARMUP_ITERATIONS = 2_000;
    private static final int MEASURED_ITERATIONS = 20_000;
    private static final String LOAN_ID = "LN-2019-00142";
    private static final String BORROWER_ID = "B-10001";

    @Autowired private LegacyLoanDataProvider legacyProvider;
    @Autowired private ModernLoanDataProvider modernProvider;

    private record Operation(String name, Consumer<LoanDataProvider> action) {}

    private record Result(String operation, double legacyMicros, double modernMicros) {
        double speedup() {
            return modernMicros == 0 ? 0 : legacyMicros / modernMicros;
        }
    }

    @Test
    void compareLegacyVsModernReadPerformance() throws IOException {
        List<Operation> operations = List.of(
                new Operation("getAllLoans", LoanDataProvider::getAllLoans),
                new Operation("getAllBorrowers", LoanDataProvider::getAllBorrowers),
                new Operation("getLoanById", p -> p.getLoanById(LOAN_ID)),
                new Operation("getPaymentsByLoan", p -> p.getPaymentsByLoan(LOAN_ID)),
                new Operation("getBorrowerById", p -> p.getBorrowerById(BORROWER_ID)));

        List<Result> results = new ArrayList<>();
        for (Operation op : operations) {
            double legacy = timeMicrosPerCall(legacyProvider, op.action());
            double modern = timeMicrosPerCall(modernProvider, op.action());
            results.add(new Result(op.name(), legacy, modern));
        }

        String report = renderReport(results);
        System.out.println(report);
        Path out = Path.of("target", "performance-comparison.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report);
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    /** Warm up the JIT, then return average wall-clock microseconds per call. */
    private double timeMicrosPerCall(LoanDataProvider provider, Consumer<LoanDataProvider> action) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            action.accept(provider);
        }
        long start = System.nanoTime();
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            action.accept(provider);
        }
        long elapsedNanos = System.nanoTime() - start;
        return (elapsedNanos / (double) MEASURED_ITERATIONS) / 1_000.0;
    }

    private String renderReport(List<Result> results) {
        double legacyTotal = results.stream().mapToDouble(Result::legacyMicros).sum();
        double modernTotal = results.stream().mapToDouble(Result::modernMicros).sum();

        StringBuilder sb = new StringBuilder();
        sb.append("# Legacy vs Modern — Read Performance Comparison\n\n");
        sb.append("Average wall-clock time per read call over ")
          .append(MEASURED_ITERATIONS).append(" iterations (after ")
          .append(WARMUP_ITERATIONS).append(" warmup), same in-memory H2 and the ")
          .append("same migrated 5/5/5/10 data set. `speedup` = legacy / modern ")
          .append("(>1 means modern is faster).\n\n");
        sb.append("| Operation | Legacy (µs/call) | Modern (µs/call) | Speedup |\n");
        sb.append("|-----------|-----------------:|-----------------:|--------:|\n");
        for (Result r : results) {
            sb.append(String.format("| %s | %.2f | %.2f | %.2fx |%n",
                    r.operation(), r.legacyMicros(), r.modernMicros(), r.speedup()));
        }
        sb.append(String.format("| **total** | **%.2f** | **%.2f** | **%.2fx** |%n",
                legacyTotal, modernTotal, modernTotal == 0 ? 0 : legacyTotal / modernTotal));
        sb.append("\n> Comparative micro-benchmark: the shared DB round-trip is a ")
          .append("constant, so the delta reflects the legacy string→type parsing ")
          .append("that the modern typed schema eliminates. Absolute numbers depend ")
          .append("on the machine/JVM; the ratio is the meaningful figure.\n");
        return sb.toString();
    }
}
