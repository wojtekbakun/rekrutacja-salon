package com.example.dealership.customer;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NipValidator implements ConstraintValidator<Nip, String> {

    private static final int[] WEIGHTS = {6, 5, 7, 2, 3, 4, 5, 6, 7};

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (!value.matches("[0-9]{10}")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (value.charAt(i) - '0') * WEIGHTS[i];
        }
        int checkDigit = sum % 11;
        return checkDigit != 10 && checkDigit == value.charAt(9) - '0';
    }
}
