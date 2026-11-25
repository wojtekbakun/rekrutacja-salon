import { Component, inject, input } from '@angular/core';
import { loadErrorMessage } from '../shared/problem-detail';
import {
  DOCUMENT_TYPE_LABELS,
  READINESS_REASON_LABELS,
  VehicleDocumentApi,
} from './vehicle-document-api';

@Component({
  selector: 'app-vehicle-readiness',
  templateUrl: './vehicle-readiness.html',
})
export class VehicleReadiness {
  private readonly vehicleDocumentApi = inject(VehicleDocumentApi);

  readonly vehicleId = input.required<number>();

  protected readonly readiness = this.vehicleDocumentApi.readiness(this.vehicleId);
  protected readonly documentTypeLabels = DOCUMENT_TYPE_LABELS;
  protected readonly reasonLabels = READINESS_REASON_LABELS;

  reload(): void {
    this.readiness.reload();
  }

  protected loadError(error: unknown): string {
    return loadErrorMessage(error, 'Vehicle not found');
  }
}
