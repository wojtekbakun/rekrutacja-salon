package com.example.dealership.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.example.dealership.error.ConflictException;
import com.example.dealership.error.NotFoundException;
import com.example.dealership.error.RequestValidationException;
import com.example.dealership.error.ValidationError;
import com.example.dealership.vehicle.FuelType;
import com.example.dealership.vehicle.Vehicle;
import com.example.dealership.vehicle.VehicleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class VehicleDocumentServiceTest {

    private static final Long VEHICLE_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final String NUMBER = "COC/2025/000001";
    private static final String OTHER_NUMBER = "OC/2026/123456";

    @Mock
    private VehicleDocumentRepository vehicleDocumentRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    private VehicleDocumentService vehicleDocumentService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);
        vehicleDocumentService = new VehicleDocumentService(vehicleDocumentRepository, vehicleRepository, clock);
    }

    @Test
    void findAllReturnsMappedDocumentsOfVehicle() {
        when(vehicleRepository.existsById(VEHICLE_ID)).thenReturn(true);
        when(vehicleDocumentRepository.findByVehicleId(VEHICLE_ID, Sort.by("id")))
                .thenReturn(List.of(document(NUMBER), document(OTHER_NUMBER)));

        List<VehicleDocumentResponse> responses = vehicleDocumentService.findAll(VEHICLE_ID);

        assertThat(responses).extracting(VehicleDocumentResponse::number).containsExactly(NUMBER, OTHER_NUMBER);
        assertThat(responses).extracting(VehicleDocumentResponse::vehicleId).containsOnly(VEHICLE_ID);
    }

    @Test
    void findAllThrowsNotFoundWhenVehicleDoesNotExist() {
        when(vehicleRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> vehicleDocumentService.findAll(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Vehicle with id 99 not found");
        verifyNoInteractions(vehicleDocumentRepository);
    }

    @Test
    void createSavesDocumentWithPendingStatus() {
        givenVehicleWithoutActiveDocument();
        when(vehicleDocumentRepository.save(any(VehicleDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VehicleDocumentResponse response = vehicleDocumentService.create(VEHICLE_ID, request(NUMBER));

        assertThat(response.vehicleId()).isEqualTo(VEHICLE_ID);
        assertThat(response.type()).isEqualTo(DocumentType.OC_POLICY);
        assertThat(response.number()).isEqualTo(NUMBER);
        assertThat(response.issueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(response.expiryDate()).isEqualTo(LocalDate.of(2027, 10, 31));
        assertThat(response.status()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void createAcceptsIssueDateToday() {
        givenVehicleWithoutActiveDocument();
        when(vehicleDocumentRepository.save(any(VehicleDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VehicleDocumentResponse response = vehicleDocumentService.create(VEHICLE_ID, request(TODAY, null));

        assertThat(response.issueDate()).isEqualTo(TODAY);
    }

    @Test
    void createAcceptsExpiryDateEqualToIssueDate() {
        givenVehicleWithoutActiveDocument();
        when(vehicleDocumentRepository.save(any(VehicleDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VehicleDocumentResponse response = vehicleDocumentService.create(VEHICLE_ID, request(TODAY, TODAY));

        assertThat(response.expiryDate()).isEqualTo(TODAY);
    }

    @Test
    void createRejectsIssueDateInTheFuture() {
        when(vehicleRepository.existsById(VEHICLE_ID)).thenReturn(true);

        assertThatThrownBy(() -> vehicleDocumentService.create(VEHICLE_ID, request(TODAY.plusDays(1), null)))
                .isInstanceOfSatisfying(RequestValidationException.class, ex -> assertThat(ex.getErrors())
                        .containsExactly(new ValidationError("issueDate", "must not be in the future")));
        verify(vehicleDocumentRepository, never()).save(any());
    }

    @Test
    void createRejectsExpiryDateBeforeIssueDate() {
        when(vehicleRepository.existsById(VEHICLE_ID)).thenReturn(true);

        assertThatThrownBy(() -> vehicleDocumentService.create(VEHICLE_ID, request(TODAY, TODAY.minusDays(1))))
                .isInstanceOfSatisfying(RequestValidationException.class, ex -> assertThat(ex.getErrors())
                        .containsExactly(new ValidationError("expiryDate", "must not be before issue date")));
        verify(vehicleDocumentRepository, never()).save(any());
    }

    @Test
    void createReportsBothDateErrorsAtOnce() {
        when(vehicleRepository.existsById(VEHICLE_ID)).thenReturn(true);

        assertThatThrownBy(() -> vehicleDocumentService.create(
                        VEHICLE_ID, request(TODAY.plusDays(5), TODAY.plusDays(2))))
                .isInstanceOfSatisfying(RequestValidationException.class, ex -> assertThat(ex.getErrors())
                        .extracting(ValidationError::field).containsExactly("issueDate", "expiryDate"));
    }

    @Test
    void createThrowsConflictWhenTypeAlreadyHasActiveDocument() {
        when(vehicleRepository.existsById(VEHICLE_ID)).thenReturn(true);
        when(vehicleDocumentRepository.existsByVehicleIdAndTypeAndStatusIn(
                VEHICLE_ID, DocumentType.OC_POLICY, List.of(DocumentStatus.PENDING, DocumentStatus.VERIFIED)))
                .thenReturn(true);

        assertThatThrownBy(() -> vehicleDocumentService.create(VEHICLE_ID, request(NUMBER)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Vehicle with id 1 already has an active OC_POLICY document");
        verify(vehicleDocumentRepository, never()).save(any());
    }

    @Test
    void createThrowsNotFoundWhenVehicleDoesNotExist() {
        when(vehicleRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> vehicleDocumentService.create(99L, request(NUMBER)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Vehicle with id 99 not found");
        verify(vehicleDocumentRepository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"PENDING, VERIFIED", "PENDING, REJECTED", "VERIFIED, REJECTED"})
    void changeStatusAppliesAllowedTransition(DocumentStatus from, DocumentStatus to) {
        VehicleDocument document = document(NUMBER, from);
        givenDocument(document);
        when(vehicleDocumentRepository.save(document)).thenReturn(document);

        VehicleDocumentResponse response = vehicleDocumentService.changeStatus(
                VEHICLE_ID, 5L, new DocumentStatusRequest(to));

        assertThat(response.status()).isEqualTo(to);
        assertThat(document.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, PENDING", "VERIFIED, PENDING", "VERIFIED, VERIFIED",
            "REJECTED, PENDING", "REJECTED, VERIFIED", "REJECTED, REJECTED"})
    void changeStatusThrowsConflictForForbiddenTransition(DocumentStatus from, DocumentStatus to) {
        givenDocument(document(NUMBER, from));

        assertThatThrownBy(() -> vehicleDocumentService.changeStatus(VEHICLE_ID, 5L, new DocumentStatusRequest(to)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Document status cannot change from " + from + " to " + to);
        verify(vehicleDocumentRepository, never()).save(any());
    }

    @Test
    void changeStatusThrowsNotFoundWhenDocumentBelongsToAnotherVehicle() {
        when(vehicleRepository.existsById(VEHICLE_ID)).thenReturn(true);
        when(vehicleDocumentRepository.findByIdAndVehicleId(5L, VEHICLE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleDocumentService.changeStatus(
                        VEHICLE_ID, 5L, new DocumentStatusRequest(DocumentStatus.VERIFIED)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Document with id 5 not found");
    }

    @Test
    void changeStatusThrowsNotFoundWhenVehicleDoesNotExist() {
        when(vehicleRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> vehicleDocumentService.changeStatus(
                        99L, 5L, new DocumentStatusRequest(DocumentStatus.VERIFIED)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Vehicle with id 99 not found");
    }

    @Test
    void handoverReadinessUsesVehicleFlagHandoverDateAndDocuments() {
        Vehicle vehicle = new Vehicle("WVWZZZ1KZAW000001", "Volkswagen", "Golf", 2025, FuelType.PETROL,
                LocalDate.of(2026, 12, 15), true);
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));
        when(vehicleDocumentRepository.findByVehicleId(VEHICLE_ID, Sort.by("id"))).thenReturn(List.of());

        HandoverReadiness readiness = vehicleDocumentService.handoverReadiness(VEHICLE_ID);

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.missingDocumentTypes()).containsExactly(DocumentType.values());
        assertThat(readiness.problems()).isEmpty();
    }

    @Test
    void handoverReadinessThrowsNotFoundWhenVehicleDoesNotExist() {
        when(vehicleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleDocumentService.handoverReadiness(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Vehicle with id 99 not found");
    }

    private void givenVehicleWithoutActiveDocument() {
        when(vehicleRepository.existsById(VEHICLE_ID)).thenReturn(true);
        when(vehicleDocumentRepository.existsByVehicleIdAndTypeAndStatusIn(eq(VEHICLE_ID), any(), anyCollection()))
                .thenReturn(false);
    }

    private void givenDocument(VehicleDocument document) {
        when(vehicleRepository.existsById(VEHICLE_ID)).thenReturn(true);
        when(vehicleDocumentRepository.findByIdAndVehicleId(5L, VEHICLE_ID)).thenReturn(Optional.of(document));
    }

    private static VehicleDocument document(String number) {
        return document(number, DocumentStatus.VERIFIED);
    }

    private static VehicleDocument document(String number, DocumentStatus status) {
        return new VehicleDocument(VEHICLE_ID, DocumentType.OC_POLICY, number, LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 10, 31), status);
    }

    private static VehicleDocumentRequest request(String number) {
        return new VehicleDocumentRequest(
                DocumentType.OC_POLICY, number, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 10, 31));
    }

    private static VehicleDocumentRequest request(LocalDate issueDate, LocalDate expiryDate) {
        return new VehicleDocumentRequest(DocumentType.OC_POLICY, NUMBER, issueDate, expiryDate);
    }
}
