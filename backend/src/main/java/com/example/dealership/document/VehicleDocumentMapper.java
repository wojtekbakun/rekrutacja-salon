package com.example.dealership.document;

public final class VehicleDocumentMapper {

    private VehicleDocumentMapper() {
    }

    // Status is server-managed: a newly added document is always PENDING.
    public static VehicleDocument toEntity(Long vehicleId, VehicleDocumentRequest request) {
        return new VehicleDocument(
                vehicleId,
                request.type(),
                request.number(),
                request.issueDate(),
                request.expiryDate(),
                DocumentStatus.PENDING);
    }

    public static VehicleDocumentResponse toResponse(VehicleDocument document) {
        return new VehicleDocumentResponse(
                document.getId(),
                document.getVehicleId(),
                document.getType(),
                document.getNumber(),
                document.getIssueDate(),
                document.getExpiryDate(),
                document.getStatus());
    }
}
