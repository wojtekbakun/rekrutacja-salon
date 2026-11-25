package com.example.dealership.document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Pure handover readiness rules (INTENT A9, assumptions Z8–Z12). No Spring and no clock: "today" is passed in.
 * Expiry is evaluated against the planned handover date, or against today when there is none
 * (a document expired today is expired for any future handover – this is what the contract example shows).
 */
public final class HandoverReadinessCalculator {

    static final Set<DocumentType> ALWAYS_REQUIRED = EnumSet.of(
            DocumentType.SALES_AGREEMENT, DocumentType.INVOICE, DocumentType.COC, DocumentType.OC_POLICY);

    private HandoverReadinessCalculator() {
    }

    public static HandoverReadiness calculate(boolean dealerRegistration, LocalDate handoverDate, LocalDate today,
            List<VehicleDocument> documents) {
        LocalDate validOn = handoverDate != null ? handoverDate : today;
        List<DocumentType> missing = new ArrayList<>();
        List<ReadinessProblem> problems = new ArrayList<>();

        for (DocumentType type : requiredTypes(dealerRegistration)) {
            Optional<VehicleDocument> active = activeDocument(type, documents);
            if (active.isEmpty()) {
                missing.add(type);
                continue;
            }
            VehicleDocument document = active.get();
            if (document.getStatus() == DocumentStatus.PENDING) {
                problems.add(new ReadinessProblem(type, ReadinessReason.NOT_VERIFIED));
            }
            // Valid on the handover day inclusive: expiring on that day is fine (INTENT A8).
            if (document.getExpiryDate() != null && document.getExpiryDate().isBefore(validOn)) {
                problems.add(new ReadinessProblem(type, ReadinessReason.EXPIRED));
            }
        }
        if (handoverDate == null) {
            problems.add(new ReadinessProblem(null, ReadinessReason.NO_HANDOVER_DATE));
        }
        return new HandoverReadiness(missing.isEmpty() && problems.isEmpty(), missing, problems);
    }

    // Enum declaration order = contract order, so the response is deterministic (Z11).
    static Set<DocumentType> requiredTypes(boolean dealerRegistration) {
        Set<DocumentType> required = EnumSet.copyOf(ALWAYS_REQUIRED);
        if (dealerRegistration) {
            required.add(DocumentType.REGISTRATION_POA);
        }
        return required;
    }

    // A rejected document counts as missing (INTENT A6). Normally at most one is active; the newest wins.
    private static Optional<VehicleDocument> activeDocument(DocumentType type, List<VehicleDocument> documents) {
        return documents.stream()
                .filter(document -> document.getType() == type && document.getStatus().isActive())
                .max(Comparator.comparing(VehicleDocument::getId, Comparator.nullsFirst(Comparator.naturalOrder())));
    }
}
