import { Component, inject, input, numberAttribute } from '@angular/core';
import { RouterLink } from '@angular/router';
import { loadErrorMessage } from '../shared/problem-detail';
import { CustomerApi } from './customer-api';

@Component({
  selector: 'app-customer-detail',
  imports: [RouterLink],
  templateUrl: './customer-detail.html',
})
export class CustomerDetail {
  private readonly customerApi = inject(CustomerApi);

  readonly id = input.required({ transform: numberAttribute });

  protected readonly customer = this.customerApi.get(this.id);

  protected loadError(error: unknown): string {
    return loadErrorMessage(error, 'Customer not found');
  }
}
