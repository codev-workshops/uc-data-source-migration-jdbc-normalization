package com.workshop.loan.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal borrower projection returned by borrower-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BorrowerRef {

    private String id;
    private String fullName;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
}
