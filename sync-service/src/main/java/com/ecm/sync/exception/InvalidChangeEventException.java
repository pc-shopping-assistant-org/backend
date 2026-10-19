package com.ecm.sync.exception;

/**
 * A {@code catalog.changed} record that can never be processed, however often it is retried: unreadable JSON, or a row
 * without the column it is looked up by. It goes to the dead-letter topic at once instead of being retried.
 */
public class InvalidChangeEventException extends RuntimeException {

    public InvalidChangeEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
