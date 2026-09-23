package com.sparsh.sentinel.ingest.transaction;

public class UnknownCustomerException extends RuntimeException {

    private final String customerId;

    public UnknownCustomerException(String customerId) {
        super("No customer with id '" + customerId + "'");
        this.customerId = customerId;
    }

    public String getCustomerId() {
        return customerId;
    }
}
