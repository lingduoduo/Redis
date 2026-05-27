package com.example.redislock.norepeat;

/**
 * Raised when Redis detects a repeated submit within the configured lock window.
 */
public class DuplicateSubmitException extends RuntimeException {

    public DuplicateSubmitException(String message) {
        super(message);
    }
}
