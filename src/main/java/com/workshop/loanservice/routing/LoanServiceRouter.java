package com.workshop.loanservice.routing;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.service.LoanQueryService;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Facade injected into the controllers. Delegates each call to the {@link LoanQueryService} chosen
 * for the current request by {@link RoutingContext}.
 */
@Service
@Primary
public class LoanServiceRouter implements LoanQueryService {

  private final LoanServiceRegistry registry;
  private final RoutingContext routingContext;

  public LoanServiceRouter(LoanServiceRegistry registry, RoutingContext routingContext) {
    this.registry = registry;
    this.routingContext = routingContext;
  }

  @Override
  public List<LoanSummaryDto> getAllLoans() {
    return delegate().getAllLoans();
  }

  @Override
  public LoanSummaryDto getLoanById(String loanAccountNumber) {
    return delegate().getLoanById(loanAccountNumber);
  }

  @Override
  public List<BorrowerDto> getAllBorrowers() {
    return delegate().getAllBorrowers();
  }

  @Override
  public BorrowerDto getBorrowerById(String borrowerId) {
    return delegate().getBorrowerById(borrowerId);
  }

  @Override
  public List<PaymentDto> getPaymentsByLoan(String loanAccountNumber) {
    return delegate().getPaymentsByLoan(loanAccountNumber);
  }

  private LoanQueryService delegate() {
    return registry.get(routingContext.getImplementation());
  }
}
