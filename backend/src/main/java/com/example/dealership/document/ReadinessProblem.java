package com.example.dealership.document;

// documentType is null for vehicle-level problems (NO_HANDOVER_DATE); it is serialized as null, not omitted.
public record ReadinessProblem(DocumentType documentType, ReadinessReason reason) {
}
