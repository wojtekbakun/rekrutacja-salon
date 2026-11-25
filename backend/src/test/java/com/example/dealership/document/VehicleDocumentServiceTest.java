package com.example.dealership.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import com.example.dealership.error.NotFoundException;
import com.example.dealership.vehicle.VehicleRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class VehicleDocumentServiceTest {

    private static final Long VEHICLE_ID = 1L;
    private static final String NUMBER = "COC/2025/000001";
    private static final String OTHER_NUMBER = "OC/2026/123456";

    @Mock
    private VehicleDocumentRepository vehicleDocumentRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private VehicleDocumentService vehicleDocumentService;

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
        when(vehicleRepository.existsById(VEHICLE_ID)).thenReturn(true);
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
    void createThrowsNotFoundWhenVehicleDoesNotExist() {
        when(vehicleRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> vehicleDocumentService.create(99L, request(NUMBER)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Vehicle with id 99 not found");
        verify(vehicleDocumentRepository, never()).save(any());
    }

    private static VehicleDocument document(String number) {
        return new VehicleDocument(VEHICLE_ID, DocumentType.OC_POLICY, number, LocalDate.of(2026, 11, 1),
                LocalDate.of(2027, 10, 31), DocumentStatus.VERIFIED);
    }

    private static VehicleDocumentRequest request(String number) {
        return new VehicleDocumentRequest(
                DocumentType.OC_POLICY, number, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 10, 31));
    }
}
