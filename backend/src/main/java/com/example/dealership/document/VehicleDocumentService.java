package com.example.dealership.document;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.example.dealership.error.ConflictException;
import com.example.dealership.error.NotFoundException;
import com.example.dealership.error.RequestValidationException;
import com.example.dealership.error.ValidationError;
import com.example.dealership.vehicle.Vehicle;
import com.example.dealership.vehicle.VehicleRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VehicleDocumentService {

    private static final List<DocumentStatus> ACTIVE_STATUSES = Arrays.stream(DocumentStatus.values())
            .filter(DocumentStatus::isActive)
            .toList();

    private final VehicleDocumentRepository vehicleDocumentRepository;
    private final VehicleRepository vehicleRepository;
    private final Clock clock;

    public VehicleDocumentService(VehicleDocumentRepository vehicleDocumentRepository,
            VehicleRepository vehicleRepository, Clock clock) {
        this.vehicleDocumentRepository = vehicleDocumentRepository;
        this.vehicleRepository = vehicleRepository;
        this.clock = clock;
    }

    public List<VehicleDocumentResponse> findAll(Long vehicleId) {
        requireVehicle(vehicleId);
        return vehicleDocumentRepository.findByVehicleId(vehicleId, Sort.by("id")).stream()
                .map(VehicleDocumentMapper::toResponse)
                .toList();
    }

    @Transactional
    public VehicleDocumentResponse create(Long vehicleId, VehicleDocumentRequest request) {
        requireVehicle(vehicleId);
        validateDates(request);
        if (vehicleDocumentRepository.existsByVehicleIdAndTypeAndStatusIn(vehicleId, request.type(), ACTIVE_STATUSES)) {
            throw new ConflictException("Vehicle with id " + vehicleId + " already has an active "
                    + request.type() + " document");
        }
        VehicleDocument document = vehicleDocumentRepository.save(VehicleDocumentMapper.toEntity(vehicleId, request));
        return VehicleDocumentMapper.toResponse(document);
    }

    @Transactional
    public VehicleDocumentResponse changeStatus(Long vehicleId, Long documentId, DocumentStatusRequest request) {
        requireVehicle(vehicleId);
        VehicleDocument document = vehicleDocumentRepository.findByIdAndVehicleId(documentId, vehicleId)
                .orElseThrow(() -> new NotFoundException("Document with id " + documentId + " not found"));
        DocumentStatus current = document.getStatus();
        if (!current.canTransitionTo(request.status())) {
            throw new ConflictException("Document status cannot change from " + current + " to " + request.status());
        }
        document.setStatus(request.status());
        return VehicleDocumentMapper.toResponse(vehicleDocumentRepository.save(document));
    }

    public HandoverReadiness handoverReadiness(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> vehicleNotFound(vehicleId));
        List<VehicleDocument> documents = vehicleDocumentRepository.findByVehicleId(vehicleId, Sort.by("id"));
        return HandoverReadinessCalculator.calculate(
                vehicle.isDealerRegistration(), vehicle.getPlannedHandoverDate(), LocalDate.now(clock), documents);
    }

    private void validateDates(VehicleDocumentRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        LocalDate today = LocalDate.now(clock);
        if (request.issueDate().isAfter(today)) {
            errors.add(new ValidationError("issueDate", "must not be in the future"));
        }
        if (request.expiryDate() != null && request.expiryDate().isBefore(request.issueDate())) {
            errors.add(new ValidationError("expiryDate", "must not be before issue date"));
        }
        if (!errors.isEmpty()) {
            throw new RequestValidationException(errors);
        }
    }

    private void requireVehicle(Long vehicleId) {
        if (!vehicleRepository.existsById(vehicleId)) {
            throw vehicleNotFound(vehicleId);
        }
    }

    private static NotFoundException vehicleNotFound(Long vehicleId) {
        return new NotFoundException("Vehicle with id " + vehicleId + " not found");
    }
}
