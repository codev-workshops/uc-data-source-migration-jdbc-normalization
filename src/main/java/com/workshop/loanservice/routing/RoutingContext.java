package com.workshop.loanservice.routing;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/** Per-request selection of the {@link ServiceImplementation}, populated by the interceptor. */
@Component
@RequestScope
public class RoutingContext {

  private ServiceImplementation implementation = ServiceImplementation.DEFAULT;

  public ServiceImplementation getImplementation() {
    return implementation;
  }

  public void setImplementation(ServiceImplementation implementation) {
    this.implementation = implementation;
  }
}
