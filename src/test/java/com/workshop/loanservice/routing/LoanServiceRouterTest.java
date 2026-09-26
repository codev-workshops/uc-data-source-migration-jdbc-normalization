package com.workshop.loanservice.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.service.LoanQueryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanServiceRouterTest {

  private static final String LOAN_ID = "LN-2019-00142";
  private static final String BORROWER_ID = "B-10001";

  @Mock private LoanQueryService legacy;
  @Mock private LoanQueryService normalized;
  @Mock private RoutingContext routingContext;

  private LoanServiceRouter router;

  @BeforeEach
  void setUp() {
    LoanServiceRegistry registry = new LoanServiceRegistry();
    registry.register(ServiceImplementation.LEGACY, legacy);
    registry.register(ServiceImplementation.NORMALIZED, normalized);
    router = new LoanServiceRouter(registry, routingContext);
  }

  @Test
  void delegatesToLegacyWhenContextSelectsLegacy() {
    given(routingContext.getImplementation()).willReturn(ServiceImplementation.LEGACY);
    List<LoanSummaryDto> loans = List.of(new LoanSummaryDto());
    given(legacy.getAllLoans()).willReturn(loans);

    assertThat(router.getAllLoans()).isSameAs(loans);
    verifyNoInteractions(normalized);
  }

  @Test
  void delegatesToNormalizedWhenContextSelectsNormalized() {
    given(routingContext.getImplementation()).willReturn(ServiceImplementation.NORMALIZED);
    LoanSummaryDto loan = new LoanSummaryDto();
    given(normalized.getLoanById(LOAN_ID)).willReturn(loan);

    assertThat(router.getLoanById(LOAN_ID)).isSameAs(loan);
    verifyNoInteractions(legacy);
  }

  @Test
  void resolvesImplementationOnEveryCall() {
    given(routingContext.getImplementation())
        .willReturn(ServiceImplementation.LEGACY, ServiceImplementation.NORMALIZED);

    router.getAllBorrowers();
    router.getAllBorrowers();

    verify(legacy).getAllBorrowers();
    verify(normalized).getAllBorrowers();
  }

  @Test
  void forwardsAllInterfaceMethods() {
    given(routingContext.getImplementation()).willReturn(ServiceImplementation.NORMALIZED);
    BorrowerDto borrower = new BorrowerDto();
    List<PaymentDto> payments = List.of(new PaymentDto());
    given(normalized.getBorrowerById(BORROWER_ID)).willReturn(borrower);
    given(normalized.getPaymentsByLoan(LOAN_ID)).willReturn(payments);

    assertThat(router.getBorrowerById(BORROWER_ID)).isSameAs(borrower);
    assertThat(router.getPaymentsByLoan(LOAN_ID)).isSameAs(payments);
  }
}
