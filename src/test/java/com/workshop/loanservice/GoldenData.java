package com.workshop.loanservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;

/** Loads golden dataset files and compares typed entity values against them. */
final class GoldenData {

  static final ObjectMapper MAPPER =
      new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

  private static final Pattern LEGACY_INSERT = Pattern.compile("INSERT INTO (\\w+) VALUES");

  private GoldenData() {}

  static List<Map<String, Object>> rows(String fileName) {
    try (InputStream in = new ClassPathResource("golden/" + fileName).getInputStream()) {
      return MAPPER.readValue(in, new TypeReference<List<Map<String, Object>>>() {});
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static JsonNode json(String fileName) {
    try (InputStream in = new ClassPathResource("golden/" + fileName).getInputStream()) {
      return MAPPER.readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Counts {@code INSERT INTO <table>} statements per legacy table in data-legacy.sql. */
  static Map<String, Integer> legacyRowCounts() {
    try (InputStream in = new ClassPathResource("legacy/data-legacy.sql").getInputStream()) {
      String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      Map<String, Integer> counts = new LinkedHashMap<>();
      Matcher m = LEGACY_INSERT.matcher(sql);
      while (m.find()) {
        counts.merge(m.group(1), 1, Integer::sum);
      }
      return counts;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Asserts that every golden field equals the corresponding typed actual value. */
  static void assertRowMatches(String rowKey, Map<String, Object> expected, Map<String, ?> actual) {
    assertEquals(expected.keySet(), actual.keySet(), rowKey + ": field set");
    expected.forEach(
        (field, value) -> assertValueMatches(rowKey + "." + field, value, actual.get(field)));
  }

  private static void assertValueMatches(String path, Object expected, Object actual) {
    if (expected == null) {
      assertNull(actual, path);
      return;
    }
    assertNotNull(actual, path);
    if (expected instanceof Number number) {
      BigDecimal want = new BigDecimal(number.toString());
      BigDecimal got = new BigDecimal(actual.toString());
      assertEquals(0, want.compareTo(got), path + ": expected " + want + " but was " + got);
    } else if (actual instanceof LocalDateTime dateTime) {
      assertEquals(expected, dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), path);
    } else if (actual instanceof LocalDate date) {
      assertEquals(expected, date.format(DateTimeFormatter.ISO_LOCAL_DATE), path);
    } else {
      assertEquals(expected, actual, path);
    }
  }
}
