import { Component, inject, input, numberAttribute } from '@angular/core';
import { RouterLink } from '@angular/router';
import { loadErrorMessage } from '../shared/problem-detail';
import { FUEL_TYPE_LABELS, VehicleApi } from './vehicle-api';
import { VehicleDocumentSection } from './vehicle-document-section';
import { VehicleReadiness } from './vehicle-readiness';

@Component({
  selector: 'app-vehicle-detail',
  imports: [RouterLink, VehicleDocumentSection, VehicleReadiness],
  templateUrl: './vehicle-detail.html',
})
export class VehicleDetail {
  private readonly vehicleApi = inject(VehicleApi);

  readonly id = input.required({ transform: numberAttribute });

  protected readonly vehicle = this.vehicleApi.get(this.id);
  protected readonly fuelTypeLabels = FUEL_TYPE_LABELS;

  protected loadError(error: unknown): string {
    return loadErrorMessage(error, 'Vehicle not found');
  }
}
