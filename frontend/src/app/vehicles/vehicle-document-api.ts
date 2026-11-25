import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, Signal, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type DocumentType = 'SALES_AGREEMENT' | 'INVOICE' | 'COC' | 'OC_POLICY' | 'REGISTRATION_POA';

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  SALES_AGREEMENT: 'Sales agreement',
  INVOICE: 'Invoice',
  COC: 'Certificate of conformity (COC)',
  OC_POLICY: 'OC insurance policy',
  REGISTRATION_POA: 'Registration power of attorney',
};

export const DOCUMENT_TYPES = Object.keys(DOCUMENT_TYPE_LABELS) as DocumentType[];

export type DocumentStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';

export const DOCUMENT_STATUS_LABELS: Record<DocumentStatus, string> = {
  PENDING: 'Pending',
  VERIFIED: 'Verified',
  REJECTED: 'Rejected',
};

// Mirrors DocumentStatus.canTransitionTo on the backend (which stays the authority and answers 409).
export const ALLOWED_STATUS_CHANGES: Record<DocumentStatus, DocumentStatus[]> = {
  PENDING: ['VERIFIED', 'REJECTED'],
  VERIFIED: ['REJECTED'],
  REJECTED: [],
};

export const STATUS_CHANGE_LABELS: Record<DocumentStatus, string> = {
  PENDING: 'Reopen',
  VERIFIED: 'Verify',
  REJECTED: 'Reject',
};

export type ReadinessReason = 'NOT_VERIFIED' | 'EXPIRED' | 'NO_HANDOVER_DATE';

export const READINESS_REASON_LABELS: Record<ReadinessReason, string> = {
  NOT_VERIFIED: 'not verified',
  EXPIRED: 'expired before the handover date',
  NO_HANDOVER_DATE: 'No planned handover date',
};

export interface HandoverReadiness {
  ready: boolean;
  missingDocumentTypes: DocumentType[];
  problems: { documentType: DocumentType | null; reason: ReadinessReason }[];
}

export interface VehicleDocument {
  id: number;
  vehicleId: number;
  type: DocumentType;
  number: string;
  issueDate: string;
  expiryDate: string | null;
  status: DocumentStatus;
}

// vehicleId comes from the URL, status is managed by the server.
export type VehicleDocumentRequest = Omit<VehicleDocument, 'id' | 'vehicleId' | 'status'>;

@Injectable({ providedIn: 'root' })
export class VehicleDocumentApi {
  private readonly http = inject(HttpClient);

  list(vehicleId: Signal<number>) {
    return httpResource<VehicleDocument[]>(() => `/api/vehicles/${vehicleId()}/documents`, {
      defaultValue: [],
    });
  }

  create(vehicleId: number, request: VehicleDocumentRequest): Observable<VehicleDocument> {
    return this.http.post<VehicleDocument>(`/api/vehicles/${vehicleId}/documents`, request);
  }

  changeStatus(
    vehicleId: number,
    documentId: number,
    status: DocumentStatus,
  ): Observable<VehicleDocument> {
    return this.http.put<VehicleDocument>(
      `/api/vehicles/${vehicleId}/documents/${documentId}/status`,
      { status },
    );
  }

  readiness(vehicleId: Signal<number>) {
    return httpResource<HandoverReadiness>(() => `/api/vehicles/${vehicleId()}/handover-readiness`);
  }
}
