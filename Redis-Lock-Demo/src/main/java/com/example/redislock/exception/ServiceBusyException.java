package com.example.redislock.exception;

/**
 * Raised when a short-lived Redis lock cannot be acquired in time.
 */
public class ServiceBusyException extends RuntimeException {

    public ServiceBusyException(String message) {
        super(message);
    }

    public ServiceBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}
