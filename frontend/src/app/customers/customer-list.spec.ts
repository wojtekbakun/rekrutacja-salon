import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CustomerList } from './customer-list';

describe('CustomerList', () => {
  let fixture: ComponentFixture<CustomerList>;
  let element: HTMLElement;
  let http: HttpTestingController;

  const customer = {
    id: 1,
    firstName: 'Jan',
    lastName: 'Kowalski',
    email: 'jan.kowalski@example.com',
    phone: '+48 600 100 200',
    nip: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(CustomerList);
    element = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function rows(): string[] {
    return Array.from(element.querySelectorAll('tbody tr'), (row) => row.textContent!.trim());
  }

  it('shows the customers returned by the API', async () => {
    http.expectOne({ method: 'GET', url: '/api/customers' }).flush([customer]);
    await fixture.whenStable();

    expect(rows()).toHaveLength(1);
    expect(rows()[0]).toContain('Kowalski');
    expect(rows()[0]).toContain('jan.kowalski@example.com');
    expect(rows()[0]).toContain('—');
  });

  it('shows the empty state', async () => {
    http.expectOne({ method: 'GET', url: '/api/customers' }).flush([]);
    await fixture.whenStable();

    expect(rows()).toEqual(['No customers yet.']);
  });

  it('deletes a customer and reloads the list', async () => {
    http.expectOne({ method: 'GET', url: '/api/customers' }).flush([customer]);
    await fixture.whenStable();

    element.querySelector<HTMLButtonElement>('tbody button')!.click();
    http.expectOne({ method: 'DELETE', url: '/api/customers/1' }).flush(null);
    await vi.waitFor(() => http.expectOne({ method: 'GET', url: '/api/customers' }).flush([]));
    await fixture.whenStable();

    expect(rows()).toEqual(['No customers yet.']);
  });

  it('shows the ProblemDetail detail when deleting fails', async () => {
    http.expectOne({ method: 'GET', url: '/api/customers' }).flush([customer]);
    await fixture.whenStable();

    element.querySelector<HTMLButtonElement>('tbody button')!.click();
    http
      .expectOne({ method: 'DELETE', url: '/api/customers/1' })
      .flush(
        { title: 'Conflict', status: 409, detail: 'Customer is in use' },
        { status: 409, statusText: 'Conflict' },
      );

    await vi.waitFor(() =>
      expect(element.querySelector('.error')?.textContent?.trim()).toBe('Customer is in use'),
    );
  });
});
