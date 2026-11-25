package com.example.dealership.error;

import java.util.List;

/**
 * A 400 for rules Bean Validation cannot express (they need the clock or stored data).
 * Rendered exactly like a Bean Validation failure: {@code errors: [{field, message}]}.
 */
public class RequestValidationException extends RuntimeException {

    private final List<ValidationError> errors;

    public RequestValidationException(List<ValidationError> errors) {
        super("Validation failed");
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> getErrors() {
        return errors;
    }
}
