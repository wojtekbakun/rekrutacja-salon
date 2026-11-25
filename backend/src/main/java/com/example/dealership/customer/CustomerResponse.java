package com.example.dealership.customer;

public record CustomerResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String nip
) {
}
