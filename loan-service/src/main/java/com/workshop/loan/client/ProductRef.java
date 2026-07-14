package com.workshop.loan.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal product projection returned by product-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductRef {

    private String code;
    private String name;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
