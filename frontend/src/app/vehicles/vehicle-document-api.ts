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
}
