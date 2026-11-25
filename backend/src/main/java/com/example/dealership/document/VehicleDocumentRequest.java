package com.example.dealership.document;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VehicleDocumentRequest(
        @NotNull
        DocumentType type,

        @NotBlank
        @Size(max = 50)
        String number,

        @NotNull
        LocalDate issueDate,

        LocalDate expiryDate
) {
}
