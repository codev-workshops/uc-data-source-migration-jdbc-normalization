package com.workshop.loanservice.routing;

import com.workshop.loanservice.service.LoanQueryService;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lookup table from {@link ServiceImplementation} to the concrete {@link LoanQueryService} bean.
 * Each concrete service registers itself on startup.
 */
@Component
public class LoanServiceRegistry {

  private static final Logger log = LoggerFactory.getLogger(LoanServiceRegistry.class);

  private final Map<ServiceImplementation, LoanQueryService> services =
      new EnumMap<>(ServiceImplementation.class);

  public void register(ServiceImplementation impl, LoanQueryService service) {
    log.info("registering {} -> {}", impl, service.getClass().getSimpleName());
    services.put(impl, service);
  }

  /**
   * Returns the service registered for {@code impl}, falling back to {@link
   * ServiceImplementation#DEFAULT} when none is registered.
   *
   * @throws IllegalStateException if neither the requested nor the default implementation is
   *     registered
   */
  public LoanQueryService get(ServiceImplementation impl) {
    LoanQueryService service = services.get(impl);
    if (service != null) {
      return service;
    }
    log.warn("no service registered for {}, falling back to {}", impl, ServiceImplementation.DEFAULT);
    LoanQueryService fallback = services.get(ServiceImplementation.DEFAULT);
    if (fallback == null) {
      throw new IllegalStateException(
          "No LoanQueryService registered for " + impl + " or " + ServiceImplementation.DEFAULT);
    }
    return fallback;
  }

  public boolean isRegistered(ServiceImplementation impl) {
    return services.containsKey(impl);
  }
}
