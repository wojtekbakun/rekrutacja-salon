import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { loadErrorMessage, problemDetailMessage } from '../shared/problem-detail';
import { Customer, CustomerApi } from './customer-api';

@Component({
  selector: 'app-customer-list',
  imports: [RouterLink],
  templateUrl: './customer-list.html',
})
export class CustomerList {
  private readonly customerApi = inject(CustomerApi);

  protected readonly customers = this.customerApi.list();
  protected readonly deleteError = signal<string | null>(null);

  protected loadError(error: unknown): string {
    return loadErrorMessage(error, 'Customers not found');
  }

  protected async delete(customer: Customer): Promise<void> {
    this.deleteError.set(null);
    try {
      await firstValueFrom(this.customerApi.delete(customer.id));
      this.customers.reload();
    } catch (error) {
      this.deleteError.set(problemDetailMessage(error));
    }
  }
}
