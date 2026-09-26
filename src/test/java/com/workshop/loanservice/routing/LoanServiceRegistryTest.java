package com.workshop.loanservice.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.workshop.loanservice.service.LoanQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoanServiceRegistryTest {

  private final LoanQueryService legacy = mock(LoanQueryService.class, "legacy");
  private final LoanQueryService normalized = mock(LoanQueryService.class, "normalized");

  private LoanServiceRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new LoanServiceRegistry();
  }

  @Test
  void getReturnsRegisteredServiceForEachImplementation() {
    registry.register(ServiceImplementation.LEGACY, legacy);
    registry.register(ServiceImplementation.NORMALIZED, normalized);

    assertThat(registry.get(ServiceImplementation.LEGACY)).isSameAs(legacy);
    assertThat(registry.get(ServiceImplementation.NORMALIZED)).isSameAs(normalized);
    assertThat(registry.isRegistered(ServiceImplementation.LEGACY)).isTrue();
    assertThat(registry.isRegistered(ServiceImplementation.NORMALIZED)).isTrue();
  }

  @Test
  void getFallsBackToNormalizedWhenRequestedImplementationIsMissing() {
    registry.register(ServiceImplementation.NORMALIZED, normalized);

    assertThat(registry.get(ServiceImplementation.LEGACY)).isSameAs(normalized);
    assertThat(registry.isRegistered(ServiceImplementation.LEGACY)).isFalse();
  }

  @Test
  void getThrowsWhenNeitherRequestedNorDefaultIsRegistered() {
    assertThatThrownBy(() -> registry.get(ServiceImplementation.LEGACY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("LEGACY")
        .hasMessageContaining("NORMALIZED");
  }

  @Test
  void registerReplacesPreviousServiceForSameImplementation() {
    LoanQueryService replacement = mock(LoanQueryService.class, "replacement");
    registry.register(ServiceImplementation.LEGACY, legacy);

    registry.register(ServiceImplementation.LEGACY, replacement);

    assertThat(registry.get(ServiceImplementation.LEGACY)).isSameAs(replacement);
  }
}
