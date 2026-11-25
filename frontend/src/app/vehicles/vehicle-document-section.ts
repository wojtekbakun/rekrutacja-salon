import { Component, inject, input, output, signal } from '@angular/core';
import { FormField, FormRoot, form, required } from '@angular/forms/signals';
import { firstValueFrom } from 'rxjs';
import {
  loadErrorMessage,
  problemDetailMessage,
  problemDetailToFormErrors,
} from '../shared/problem-detail';
import {
  ALLOWED_STATUS_CHANGES,
  DOCUMENT_STATUS_LABELS,
  DOCUMENT_TYPES,
  DOCUMENT_TYPE_LABELS,
  DocumentStatus,
  DocumentType,
  STATUS_CHANGE_LABELS,
  VehicleDocument,
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
  /** Emitted after a document is added or its status changes, so the readiness can be reloaded. */
  readonly changed = output<void>();

  protected readonly documents = this.vehicleDocumentApi.list(this.vehicleId);
  protected readonly documentTypes = DOCUMENT_TYPES;
  protected readonly documentTypeLabels = DOCUMENT_TYPE_LABELS;
  protected readonly documentStatusLabels = DOCUMENT_STATUS_LABELS;
  protected readonly allowedStatusChanges = ALLOWED_STATUS_CHANGES;
  protected readonly statusChangeLabels = STATUS_CHANGE_LABELS;
  protected readonly actionError = signal<string | null>(null);

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
            this.changed.emit();
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

  protected async changeStatus(document: VehicleDocument, status: DocumentStatus): Promise<void> {
    this.actionError.set(null);
    try {
      await firstValueFrom(
        this.vehicleDocumentApi.changeStatus(this.vehicleId(), document.id, status),
      );
      this.documents.reload();
      this.changed.emit();
    } catch (error) {
      this.actionError.set(problemDetailMessage(error));
    }
  }

  protected loadError(error: unknown): string {
    return loadErrorMessage(error, 'Vehicle not found');
  }
}
