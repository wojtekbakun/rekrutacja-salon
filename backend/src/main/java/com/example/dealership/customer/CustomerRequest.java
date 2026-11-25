package com.example.dealership.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank
        @Size(max = 50)
        String firstName,

        @NotBlank
        @Size(max = 50)
        String lastName,

        @NotBlank
        @Email
        @Size(max = 100)
        String email,

        @NotBlank
        @Pattern(regexp = "\\+?[0-9]( ?[0-9]){8,14}",
                message = "must be 9 to 15 digits, optionally starting with + and separated by single spaces")
        String phone,

        @Nip
        String nip
) {

    public CustomerRequest {
        // Bean Validation runs on the trimmed value; the mapper lowercases for case-insensitive uniqueness.
        email = email == null ? null : email.trim();
    }
}
