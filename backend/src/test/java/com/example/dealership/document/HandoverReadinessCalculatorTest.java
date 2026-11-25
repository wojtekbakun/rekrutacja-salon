package com.example.dealership.document;

import static com.example.dealership.document.DocumentStatus.PENDING;
import static com.example.dealership.document.DocumentStatus.REJECTED;
import static com.example.dealership.document.DocumentStatus.VERIFIED;
import static com.example.dealership.document.DocumentType.COC;
import static com.example.dealership.document.DocumentType.INVOICE;
import static com.example.dealership.document.DocumentType.OC_POLICY;
import static com.example.dealership.document.DocumentType.REGISTRATION_POA;
import static com.example.dealership.document.DocumentType.SALES_AGREEMENT;
import static com.example.dealership.document.ReadinessReason.EXPIRED;
import static com.example.dealership.document.ReadinessReason.NOT_VERIFIED;
import static com.example.dealership.document.ReadinessReason.NO_HANDOVER_DATE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class HandoverReadinessCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final LocalDate HANDOVER = LocalDate.of(2026, 12, 15);

    private long nextId = 1;

    @Test
    void readyWhenAllFourRequiredDocumentsAreVerifiedAndValid() {
        HandoverReadiness readiness = calculate(false, HANDOVER, completeSet());

        assertThat(readiness).isEqualTo(new HandoverReadiness(true, List.of(), List.of()));
    }

    @Test
    void noDocumentsMeansAllFourRequiredTypesMissingInContractOrder() {
        HandoverReadiness readiness = calculate(false, HANDOVER, List.of());

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.missingDocumentTypes()).containsExactly(SALES_AGREEMENT, INVOICE, COC, OC_POLICY);
        assertThat(readiness.problems()).isEmpty();
    }

    @Test
    void registrationPowerOfAttorneyIsRequiredOnlyWhenDealerRegisters() {
        assertThat(calculate(true, HANDOVER, completeSet()).missingDocumentTypes()).containsExactly(REGISTRATION_POA);
        assertThat(calculate(false, HANDOVER, completeSet()).ready()).isTrue();
    }

    @Test
    void registrationPowerOfAttorneyIsIgnoredWhenCustomerRegisters() {
        List<VehicleDocument> documents = completeSet();
        documents.add(document(REGISTRATION_POA, PENDING, HANDOVER.minusDays(30)));

        assertThat(calculate(false, HANDOVER, documents).ready()).isTrue();
    }

    @Test
    void documentExpiringOnHandoverDayIsValid() {
        List<VehicleDocument> documents = completeSet(OC_POLICY, document(OC_POLICY, VERIFIED, HANDOVER));

        assertThat(calculate(false, HANDOVER, documents).ready()).isTrue();
    }

    @Test
    void documentExpiringDayBeforeHandoverIsExpired() {
        List<VehicleDocument> documents = completeSet(OC_POLICY, document(OC_POLICY, VERIFIED, HANDOVER.minusDays(1)));

        HandoverReadiness readiness = calculate(false, HANDOVER, documents);

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.missingDocumentTypes()).isEmpty();
        assertThat(readiness.problems()).containsExactly(new ReadinessProblem(OC_POLICY, EXPIRED));
    }

    @Test
    void pendingDocumentIsNotVerified() {
        List<VehicleDocument> documents = completeSet(COC, document(COC, PENDING, null));

        assertThat(calculate(false, HANDOVER, documents).problems())
                .containsExactly(new ReadinessProblem(COC, NOT_VERIFIED));
    }

    @Test
    void pendingAndExpiredDocumentReportsBothProblems() {
        List<VehicleDocument> documents = completeSet(OC_POLICY, document(OC_POLICY, PENDING, HANDOVER.minusDays(1)));

        assertThat(calculate(false, HANDOVER, documents).problems()).containsExactly(
                new ReadinessProblem(OC_POLICY, NOT_VERIFIED), new ReadinessProblem(OC_POLICY, EXPIRED));
    }

    @Test
    void onlyRejectedDocumentCountsAsMissing() {
        List<VehicleDocument> documents = completeSet(INVOICE, document(INVOICE, REJECTED, null));

        HandoverReadiness readiness = calculate(false, HANDOVER, documents);

        assertThat(readiness.missingDocumentTypes()).containsExactly(INVOICE);
        assertThat(readiness.problems()).isEmpty();
    }

    @Test
    void rejectedDocumentInHistoryDoesNotHideTheActiveReplacement() {
        List<VehicleDocument> documents = completeSet();
        documents.add(0, document(INVOICE, REJECTED, null));

        assertThat(calculate(false, HANDOVER, documents).ready()).isTrue();
    }

    @Test
    void missingHandoverDateIsNeverReadyAndIsReportedLastWithNullDocumentType() {
        List<VehicleDocument> documents = completeSet(COC, document(COC, PENDING, null));

        HandoverReadiness readiness = calculate(false, null, documents);

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.problems()).containsExactly(
                new ReadinessProblem(COC, NOT_VERIFIED), new ReadinessProblem(null, NO_HANDOVER_DATE));
    }

    @Test
    void withoutHandoverDateExpiryIsCheckedAgainstToday() {
        List<VehicleDocument> documents = completeSet(OC_POLICY, document(OC_POLICY, VERIFIED, TODAY.minusDays(1)));

        assertThat(calculate(false, null, documents).problems()).containsExactly(
                new ReadinessProblem(OC_POLICY, EXPIRED), new ReadinessProblem(null, NO_HANDOVER_DATE));
    }

    @Test
    void withoutHandoverDateDocumentExpiringTodayIsNotExpired() {
        List<VehicleDocument> documents = completeSet(OC_POLICY, document(OC_POLICY, VERIFIED, TODAY));

        assertThat(calculate(false, null, documents).problems())
                .containsExactly(new ReadinessProblem(null, NO_HANDOVER_DATE));
    }

    @Test
    void handoverDateWinsOverTodayForExpiry() {
        // Valid today, but not on the handover day.
        List<VehicleDocument> documents = completeSet(OC_POLICY, document(OC_POLICY, VERIFIED, HANDOVER.minusDays(1)));

        assertThat(calculate(false, HANDOVER, documents).problems())
                .containsExactly(new ReadinessProblem(OC_POLICY, EXPIRED));
    }

    @Test
    void exampleFromTheContract() {
        List<VehicleDocument> documents = new ArrayList<>(List.of(
                document(SALES_AGREEMENT, VERIFIED, null),
                document(INVOICE, VERIFIED, null),
                document(OC_POLICY, VERIFIED, LocalDate.of(2026, 1, 1))));

        HandoverReadiness readiness = calculate(false, null, documents);

        // BRIEF: {"ready": false, "missingDocumentTypes": ["COC"], "problems": [OC_POLICY EXPIRED, null NO_HANDOVER_DATE]}
        assertThat(readiness).isEqualTo(new HandoverReadiness(false, List.of(COC), List.of(
                new ReadinessProblem(OC_POLICY, EXPIRED), new ReadinessProblem(null, NO_HANDOVER_DATE))));
    }

    private static HandoverReadiness calculate(boolean dealerRegistration, LocalDate handoverDate,
            List<VehicleDocument> documents) {
        return HandoverReadinessCalculator.calculate(dealerRegistration, handoverDate, TODAY, documents);
    }

    private List<VehicleDocument> completeSet() {
        return new ArrayList<>(List.of(
                document(SALES_AGREEMENT, VERIFIED, null),
                document(INVOICE, VERIFIED, null),
                document(COC, VERIFIED, null),
                document(OC_POLICY, VERIFIED, HANDOVER.plusYears(1))));
    }

    private List<VehicleDocument> completeSet(DocumentType replaced, VehicleDocument replacement) {
        List<VehicleDocument> documents = completeSet();
        documents.removeIf(document -> document.getType() == replaced);
        documents.add(replacement);
        return documents;
    }

    private VehicleDocument document(DocumentType type, DocumentStatus status, LocalDate expiryDate) {
        VehicleDocument document = new VehicleDocument(
                1L, type, type + "/1", LocalDate.of(2026, 11, 1), expiryDate, status);
        ReflectionTestUtils.setField(document, "id", nextId++);
        return document;
    }
}
