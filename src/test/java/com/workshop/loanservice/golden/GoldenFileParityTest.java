package com.workshop.loanservice.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Replays every endpoint against the golden files in src/test/resources/golden and
 * asserts the JSON is semantically identical (see the README in that folder).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:goldentest;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class GoldenFileParityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @TestFactory
    Stream<DynamicTest> endpointsMatchGoldenFiles() throws IOException {
        Resource[] goldenFiles = new PathMatchingResourcePatternResolver()
                .getResources("classpath:golden/*.json");
        assertThat(goldenFiles).as("golden files present").isNotEmpty();

        return Arrays.stream(goldenFiles)
                .sorted(Comparator.comparing(Resource::getFilename))
                .map(resource -> {
                    String path = endpointFor(resource.getFilename());
                    return DynamicTest.dynamicTest(path + " == " + resource.getFilename(),
                            () -> assertMatchesGolden(path, resource));
                });
    }

    /**
     * Derives the full endpoint set from the live list endpoints (every loan detail, every
     * loan's payments, every borrower detail) and checks that each one has a golden file, so
     * a loan or borrower that appears after migration but was never captured cannot slip by.
     */
    @Test
    void everyLiveEndpointHasAGoldenFile() throws Exception {
        Set<String> goldenPaths = new HashSet<>();
        for (Resource golden : new PathMatchingResourcePatternResolver().getResources("classpath:golden/*.json")) {
            goldenPaths.add(endpointFor(golden.getFilename()));
        }

        Set<String> livePaths = new HashSet<>();
        livePaths.add("/api/loans");
        livePaths.add("/api/borrowers");
        for (JsonNode loan : fetch("/api/loans")) {
            String loanId = loan.get("loanAccountNumber").asText();
            livePaths.add("/api/loans/" + loanId);
            livePaths.add("/api/loans/" + loanId + "/payments");
        }
        for (JsonNode borrower : fetch("/api/borrowers")) {
            livePaths.add("/api/borrowers/" + borrower.get("id").asText());
        }

        assertThat(livePaths).as("5 loans + 5 borrowers -> 2 list + 15 detail endpoints").hasSize(17);
        assertThat(goldenPaths).as("golden files cover exactly the live endpoint set")
                .containsExactlyInAnyOrderElementsOf(livePaths);
    }

    private JsonNode fetch(String path) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void assertMatchesGolden(String path, Resource golden) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode actual = objectMapper.readTree(body);
        JsonNode expected;
        try (InputStream in = golden.getInputStream()) {
            expected = objectMapper.readTree(in);
        }
        assertThat(actual).as("response of GET %s vs %s", path, golden.getFilename())
                .isEqualTo(expected);
    }

    static String endpointFor(String fileName) {
        String name = fileName.substring(0, fileName.length() - ".json".length());
        if (name.equals("loans")) {
            return "/api/loans";
        }
        if (name.equals("borrowers")) {
            return "/api/borrowers";
        }
        if (name.startsWith("loan_")) {
            return "/api/loans/" + name.substring("loan_".length());
        }
        if (name.startsWith("payments_")) {
            return "/api/loans/" + name.substring("payments_".length()) + "/payments";
        }
        if (name.startsWith("borrower_")) {
            return "/api/borrowers/" + name.substring("borrower_".length());
        }
        throw new IllegalArgumentException("Unrecognised golden file name: " + fileName);
    }
}
