import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, Signal, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface Customer {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  nip: string | null;
}

export type CustomerRequest = Omit<Customer, 'id'>;

@Injectable({ providedIn: 'root' })
export class CustomerApi {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/customers';

  list() {
    return httpResource<Customer[]>(() => this.url, { defaultValue: [] });
  }

  get(id: Signal<number | undefined>) {
    return httpResource<Customer>(() => {
      const customerId = id();
      return customerId === undefined ? undefined : `${this.url}/${customerId}`;
    });
  }

  create(request: CustomerRequest): Observable<Customer> {
    return this.http.post<Customer>(this.url, request);
  }

  update(id: number, request: CustomerRequest): Observable<Customer> {
    return this.http.put<Customer>(`${this.url}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
