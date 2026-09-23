package com.sparsh.sentinel.scoring.event;

/** A record this service cannot score however often it retries. Goes straight to the DLT. */
public class UnsupportedEventException extends RuntimeException {

    public UnsupportedEventException(String message) {
        super(message);
    }
}
