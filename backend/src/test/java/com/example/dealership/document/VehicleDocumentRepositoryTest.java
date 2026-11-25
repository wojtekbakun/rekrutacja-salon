package com.example.dealership.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.example.dealership.vehicle.FuelType;
import com.example.dealership.vehicle.Vehicle;
import com.example.dealership.vehicle.VehicleRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

@DataJpaTest
class VehicleDocumentRepositoryTest {

    @Autowired
    private VehicleDocumentRepository vehicleDocumentRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void saveStoresDocumentThatCanBeReadBack() {
        Long vehicleId = vehicle("WF0XXXGCDX0000004").getId();
        VehicleDocument saved = vehicleDocumentRepository.save(new VehicleDocument(vehicleId, DocumentType.OC_POLICY,
                "OC/2026/123456", LocalDate.of(2026, 11, 1), LocalDate.of(2027, 10, 31), DocumentStatus.PENDING));
        entityManager.flush();
        entityManager.clear();

        VehicleDocument found = vehicleDocumentRepository.findById(saved.getId()).orElseThrow();

        assertThat(found).isNotSameAs(saved);
        assertThat(found.getVehicleId()).isEqualTo(vehicleId);
        assertThat(found.getType()).isEqualTo(DocumentType.OC_POLICY);
        assertThat(found.getNumber()).isEqualTo("OC/2026/123456");
        assertThat(found.getIssueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(found.getExpiryDate()).isEqualTo(LocalDate.of(2027, 10, 31));
        assertThat(found.getStatus()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void findByVehicleIdReturnsOnlyDocumentsOfThatVehicleSortedById() {
        Long vehicleId = vehicle("WF0XXXGCDX0000004").getId();
        Long otherVehicleId = vehicle("WF0XXXGCDX0000005").getId();
        VehicleDocument first = vehicleDocumentRepository.save(document(vehicleId, "INV/1"));
        vehicleDocumentRepository.save(document(otherVehicleId, "INV/2"));
        VehicleDocument third = vehicleDocumentRepository.save(document(vehicleId, "INV/3"));
        entityManager.flush();
        entityManager.clear();

        assertThat(vehicleDocumentRepository.findByVehicleId(vehicleId, Sort.by("id")))
                .extracting(VehicleDocument::getId)
                .containsExactly(first.getId(), third.getId());
    }

    @Test
    void saveRejectsDocumentOfUnknownVehicle() {
        VehicleDocument orphan = document(999_999L, "INV/1");

        assertThatThrownBy(() -> vehicleDocumentRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingVehicleDeletesItsDocuments() {
        Vehicle vehicle = vehicle("WF0XXXGCDX0000004");
        VehicleDocument document = vehicleDocumentRepository.save(document(vehicle.getId(), "INV/1"));
        entityManager.flush();

        vehicleRepository.delete(vehicle);
        entityManager.flush();
        entityManager.clear();

        assertThat(vehicleDocumentRepository.findById(document.getId())).isEmpty();
    }

    private Vehicle vehicle(String vin) {
        return vehicleRepository.saveAndFlush(new Vehicle(vin, "Ford", "Focus", 2026, FuelType.ELECTRIC, null, false));
    }

    private static VehicleDocument document(Long vehicleId, String number) {
        return new VehicleDocument(vehicleId, DocumentType.INVOICE, number, LocalDate.of(2026, 10, 5), null,
                DocumentStatus.PENDING);
    }
}
