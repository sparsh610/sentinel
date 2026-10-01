package com.sparsh.sentinel.copilot.agent.client;

/** Another Sentinel service could not be reached. Named, so the 503 says which one. */
public class ServiceUnavailableException extends RuntimeException {

    private final String service;

    public ServiceUnavailableException(String service, Throwable cause) {
        super(service + " is not reachable", cause);
        this.service = service;
    }

    public String getService() {
        return service;
    }
}
