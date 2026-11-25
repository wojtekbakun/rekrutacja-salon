import { Component, inject, input, signal } from '@angular/core';
import { FormField, FormRoot, form, required } from '@angular/forms/signals';
import { firstValueFrom } from 'rxjs';
import { loadErrorMessage, problemDetailToFormErrors } from '../shared/problem-detail';
import {
  DOCUMENT_STATUS_LABELS,
  DOCUMENT_TYPES,
  DOCUMENT_TYPE_LABELS,
  DocumentType,
  VehicleDocumentApi,
  VehicleDocumentRequest,
} from './vehicle-document-api';

interface VehicleDocumentFormModel {
  type: DocumentType | '';
  number: string;
  issueDate: string;
  expiryDate: string;
}

function emptyModel(): VehicleDocumentFormModel {
  return {
    type: '',
    number: '',
    issueDate: '',
    expiryDate: '',
  };
}

function toRequest(model: VehicleDocumentFormModel): VehicleDocumentRequest {
  return {
    type: model.type as DocumentType,
    number: model.number,
    issueDate: model.issueDate,
    expiryDate: model.expiryDate || null,
  };
}

@Component({
  selector: 'app-vehicle-document-section',
  imports: [FormField, FormRoot],
  templateUrl: './vehicle-document-section.html',
})
export class VehicleDocumentSection {
  private readonly vehicleDocumentApi = inject(VehicleDocumentApi);

  readonly vehicleId = input.required<number>();

  protected readonly documents = this.vehicleDocumentApi.list(this.vehicleId);
  protected readonly documentTypes = DOCUMENT_TYPES;
  protected readonly documentTypeLabels = DOCUMENT_TYPE_LABELS;
  protected readonly documentStatusLabels = DOCUMENT_STATUS_LABELS;

  private readonly model = signal(emptyModel());

  protected readonly documentForm = form(
    this.model,
    (path) => {
      required(path.type, { message: 'Type is required' });
      required(path.number, { message: 'Number is required' });
      required(path.issueDate, { message: 'Issue date is required' });
    },
    {
      submission: {
        action: async (documentForm) => {
          const request = toRequest(documentForm().value());
          try {
            await firstValueFrom(this.vehicleDocumentApi.create(this.vehicleId(), request));
            this.documents.reload();
            // reset() also clears touched, so the emptied required fields show no errors.
            documentForm().reset(emptyModel());
            return undefined;
          } catch (error) {
            return problemDetailToFormErrors(error, documentForm);
          }
        },
      },
    },
  );

  protected loadError(error: unknown): string {
    return loadErrorMessage(error, 'Vehicle not found');
  }
}
