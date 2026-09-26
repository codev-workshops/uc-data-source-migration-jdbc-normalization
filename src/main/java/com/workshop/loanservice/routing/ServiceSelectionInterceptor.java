package com.workshop.loanservice.routing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Reads the {@value #PARAM} query parameter and stores the resolved {@link ServiceImplementation}
 * on the request-scoped {@link RoutingContext}.
 */
@Component
public class ServiceSelectionInterceptor implements HandlerInterceptor {

  public static final String PARAM = "serviceImpl";

  private static final Logger log = LoggerFactory.getLogger(ServiceSelectionInterceptor.class);

  private final RoutingContext routingContext;

  public ServiceSelectionInterceptor(RoutingContext routingContext) {
    this.routingContext = routingContext;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String requestedImplementation = request.getParameter(PARAM);
    ServiceImplementation selectedImplementation;
    if (requestedImplementation == null) {
      selectedImplementation = ServiceImplementation.DEFAULT;
      log.info(
          "{} not provided, using default {} for {}",
          PARAM,
          selectedImplementation,
          request.getRequestURI());
    } else {
      selectedImplementation = ServiceImplementation.fromParam(requestedImplementation);
      log.info(
          "{}={} resolved to {} for {}",
          PARAM,
          requestedImplementation,
          selectedImplementation,
          request.getRequestURI());
    }
    routingContext.setImplementation(selectedImplementation);
    return true;
  }
}
