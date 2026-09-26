package com.workshop.loanservice.config;

import com.workshop.loanservice.routing.RoutingContext;
import com.workshop.loanservice.routing.ServiceSelectionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link ServiceSelectionInterceptor} for the API. {@link RoutingContext} is imported so
 * the interceptor resolves in {@code @WebMvcTest} slices, which pick up {@code WebMvcConfigurer}
 * and {@code HandlerInterceptor} beans but not plain components.
 */
@Configuration
@Import(RoutingContext.class)
public class WebConfig implements WebMvcConfigurer {

  private final ServiceSelectionInterceptor serviceSelectionInterceptor;

  public WebConfig(ServiceSelectionInterceptor serviceSelectionInterceptor) {
    this.serviceSelectionInterceptor = serviceSelectionInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(serviceSelectionInterceptor).addPathPatterns("/api/**");
  }
}
