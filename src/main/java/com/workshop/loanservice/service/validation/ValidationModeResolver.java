package com.workshop.loanservice.service.validation;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Decides whether the current API request runs in validation mode.
 *
 * <p>The {@code validate} query parameter ({@code true}/{@code false}) wins when present; otherwise
 * the {@code loanservice.validation.mode} property ({@code on}/{@code off}) applies.
 */
@Component
public class ValidationModeResolver {

  /** Query parameter that toggles validation mode per request. */
  public static final String QUERY_PARAM = "validate";

  private final boolean defaultOn;

  public ValidationModeResolver(@Value("${loanservice.validation.mode:off}") String mode) {
    this.defaultOn = "on".equalsIgnoreCase(mode) || "true".equalsIgnoreCase(mode);
  }

  /** Returns true when the current request (if any) should validate and quarantine inputs. */
  public boolean isEnabled() {
    HttpServletRequest request = currentRequest();
    if (request != null) {
      String param = request.getParameter(QUERY_PARAM);
      if (param != null) {
        return param.isBlank() || Boolean.parseBoolean(param);
      }
    }
    return defaultOn;
  }

  /** Returns {@code METHOD /pattern} for the current request, or {@code n/a} outside one. */
  public String currentEndpoint() {
    HttpServletRequest request = currentRequest();
    if (request == null) {
      return "n/a";
    }
    Object pattern =
        request.getAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern");
    String path = pattern != null ? pattern.toString() : request.getRequestURI();
    return request.getMethod() + " " + path;
  }

  private static HttpServletRequest currentRequest() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      return attrs.getRequest();
    }
    return null;
  }
}
