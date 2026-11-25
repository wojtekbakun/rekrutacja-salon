package com.example.dealership.document;

import java.util.Set;

public enum DocumentStatus {
    PENDING,
    VERIFIED,
    REJECTED;

    /**
     * The only place that decides allowed status changes (INTENT A7).
     * REJECTED is final: a corrected document is added as a new one (INTENT K1).
     * VERIFIED -> REJECTED is allowed so an error found after verification does not block the vehicle (Z3).
     */
    public boolean canTransitionTo(DocumentStatus target) {
        return switch (this) {
            case PENDING -> Set.of(VERIFIED, REJECTED).contains(target);
            case VERIFIED -> target == REJECTED;
            case REJECTED -> false;
        };
    }

    public boolean isActive() {
        return this != REJECTED;
    }
}
