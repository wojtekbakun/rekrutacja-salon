package com.example.dealership.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NipValidatorTest {

    private final NipValidator validator = new NipValidator();

    @ParameterizedTest
    @ValueSource(strings = {"1234563218", "5260250995", "7770001111", "9510002221"})
    void acceptsNipWithValidChecksum(String nip) {
        assertThat(validator.isValid(nip, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1234563210",   // wrong check digit
            "1234567890",   // checksum 10 is never valid
            "123456321",    // too short
            "12345632180",  // too long
            "123-456-32-18", // separators
            "123456321A",   // non-digit
            " 1234563218",  // surrounding whitespace
            ""
    })
    void rejectsInvalidNip(String nip) {
        assertThat(validator.isValid(nip, null)).isFalse();
    }

    @Test
    void treatsNullAsValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
