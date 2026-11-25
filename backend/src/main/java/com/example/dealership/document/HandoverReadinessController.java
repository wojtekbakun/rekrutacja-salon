package com.example.dealership.document;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HandoverReadinessController {

    private final VehicleDocumentService vehicleDocumentService;

    public HandoverReadinessController(VehicleDocumentService vehicleDocumentService) {
        this.vehicleDocumentService = vehicleDocumentService;
    }

    @GetMapping("/api/vehicles/{vehicleId}/handover-readiness")
    public HandoverReadiness handoverReadiness(@PathVariable Long vehicleId) {
        return vehicleDocumentService.handoverReadiness(vehicleId);
    }
}
