package com.workshop.loanservice.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceImplementationTest {

  @ParameterizedTest
  @ValueSource(strings = {"legacy", "LEGACY", "Legacy", " legacy "})
  void fromParamParsesLegacyCaseInsensitively(String value) {
    assertThat(ServiceImplementation.fromParam(value)).isEqualTo(ServiceImplementation.LEGACY);
  }

  @ParameterizedTest
  @ValueSource(strings = {"normalized", "NORMALIZED", "Normalized"})
  void fromParamParsesNormalizedCaseInsensitively(String value) {
    assertThat(ServiceImplementation.fromParam(value))
        .isEqualTo(ServiceImplementation.NORMALIZED);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "modern", "legacy2", "LEGACY NORMALIZED"})
  void fromParamDefaultsToNormalizedForMissingOrUnknownValues(String value) {
    assertThat(ServiceImplementation.fromParam(value)).isEqualTo(ServiceImplementation.NORMALIZED);
  }

  @Test
  void defaultIsNormalized() {
    assertThat(ServiceImplementation.DEFAULT).isEqualTo(ServiceImplementation.NORMALIZED);
  }
}
