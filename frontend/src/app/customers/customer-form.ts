import { Component, computed, inject, input, linkedSignal } from '@angular/core';
import { FormField, FormRoot, form, required } from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { loadErrorMessage, problemDetailToFormErrors } from '../shared/problem-detail';
import { optionalNumberAttribute } from '../shared/route-params';
import { Customer, CustomerApi, CustomerRequest } from './customer-api';

interface CustomerFormModel {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  nip: string;
}

function toFormModel(customer: Customer | undefined): CustomerFormModel {
  return {
    firstName: customer?.firstName ?? '',
    lastName: customer?.lastName ?? '',
    email: customer?.email ?? '',
    phone: customer?.phone ?? '',
    nip: customer?.nip ?? '',
  };
}

function toRequest(model: CustomerFormModel): CustomerRequest {
  return {
    firstName: model.firstName,
    lastName: model.lastName,
    email: model.email,
    phone: model.phone,
    nip: model.nip || null,
  };
}

@Component({
  selector: 'app-customer-form',
  imports: [FormField, FormRoot, RouterLink],
  templateUrl: './customer-form.html',
})
export class CustomerForm {
  private readonly customerApi = inject(CustomerApi);
  private readonly router = inject(Router);

  readonly id = input(undefined, { transform: optionalNumberAttribute });

  protected readonly isEdit = computed(() => this.id() !== undefined);
  protected readonly customer = this.customerApi.get(this.id);

  private readonly model = linkedSignal(() =>
    toFormModel(this.customer.hasValue() ? this.customer.value() : undefined),
  );

  protected readonly customerForm = form(
    this.model,
    (path) => {
      required(path.firstName, { message: 'First name is required' });
      required(path.lastName, { message: 'Last name is required' });
      required(path.email, { message: 'Email is required' });
      required(path.phone, { message: 'Phone is required' });
    },
    {
      submission: {
        action: async (customerForm) => {
          const request = toRequest(customerForm().value());
          const id = this.id();
          try {
            const saved = await firstValueFrom(
              id === undefined
                ? this.customerApi.create(request)
                : this.customerApi.update(id, request),
            );
            await this.router.navigate(['/customers', saved.id]);
            return undefined;
          } catch (error) {
            return problemDetailToFormErrors(error, customerForm);
          }
        },
      },
    },
  );

  protected loadError(error: unknown): string {
    return loadErrorMessage(error, 'Customer not found');
  }
}
