package com.example.dealership.document;

import jakarta.validation.constraints.NotNull;

public record DocumentStatusRequest(
        @NotNull
        DocumentStatus status
) {
}
