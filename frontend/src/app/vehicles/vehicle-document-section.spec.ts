import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { VehicleDocumentSection } from './vehicle-document-section';

describe('VehicleDocumentSection', () => {
  let fixture: ComponentFixture<VehicleDocumentSection>;
  let element: HTMLElement;
  let http: HttpTestingController;

  const url = '/api/vehicles/1/documents';
  const document = {
    id: 5,
    vehicleId: 1,
    type: 'OC_POLICY',
    number: 'OC/2026/123456',
    issueDate: '2026-11-01',
    expiryDate: null,
    status: 'PENDING',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(VehicleDocumentSection);
    fixture.componentRef.setInput('vehicleId', 1);
    element = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function rows(): string[] {
    return Array.from(element.querySelectorAll('tbody tr'), (row) => row.textContent!.trim());
  }

  function fill(type: string, index: number, value: string): void {
    const input = element.querySelectorAll<HTMLInputElement>(`input[type="${type}"]`)[index];
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function select(value: string): void {
    const select = element.querySelector('select')!;
    select.value = value;
    select.dispatchEvent(new Event('input'));
  }

  function fillValidDocument(): void {
    select('OC_POLICY');
    fill('text', 0, 'OC/2026/123456');
    fill('date', 0, '2026-11-01');
  }

  async function submit(): Promise<void> {
    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function errors(): string[] {
    return Array.from(element.querySelectorAll('.error'), (error) => error.textContent!.trim());
  }

  async function loadDocuments(documents: unknown[]): Promise<void> {
    http.expectOne({ method: 'GET', url }).flush(documents);
    await fixture.whenStable();
  }

  it('shows the documents of the given vehicle', async () => {
    await loadDocuments([document]);

    expect(rows()).toHaveLength(1);
    expect(rows()[0]).toContain('OC insurance policy');
    expect(rows()[0]).toContain('OC/2026/123456');
    expect(rows()[0]).toContain('2026-11-01');
    expect(rows()[0]).toContain('—');
    expect(rows()[0]).toContain('Pending');
  });

  it('shows the empty state', async () => {
    await loadDocuments([]);

    expect(rows()).toEqual(['No documents yet.']);
  });

  it('shows a dedicated message when the vehicle does not exist', async () => {
    http
      .expectOne({ method: 'GET', url })
      .flush(
        { title: 'Not Found', status: 404, detail: 'Vehicle with id 1 not found' },
        { status: 404, statusText: 'Not Found' },
      );

    await vi.waitFor(() => expect(errors()).toEqual(['Vehicle not found']));
  });

  it('shows required errors and sends nothing when the form is empty', async () => {
    await loadDocuments([]);

    await submit();

    http.expectNone({ method: 'POST', url });
    expect(errors()).toEqual(['Type is required', 'Number is required', 'Issue date is required']);
  });

  it('adds the document, reloads the list and clears the form', async () => {
    await loadDocuments([]);
    fillValidDocument();

    await submit();
    const request = http.expectOne({ method: 'POST', url });
    expect(request.request.body).toEqual({
      type: 'OC_POLICY',
      number: 'OC/2026/123456',
      issueDate: '2026-11-01',
      expiryDate: null,
    });
    request.flush(document);
    await vi.waitFor(() => http.expectOne({ method: 'GET', url }).flush([document]));
    await fixture.whenStable();

    expect(rows()).toHaveLength(1);
    expect(rows()[0]).toContain('OC/2026/123456');
    expect(element.querySelector('select')!.value).toBe('');
    expect(element.querySelector<HTMLInputElement>('input[type="text"]')!.value).toBe('');
    expect(errors()).toEqual([]);
  });

  it('sends the expiry date when it is filled', async () => {
    await loadDocuments([]);
    fillValidDocument();
    fill('date', 1, '2027-10-31');

    await submit();
    const request = http.expectOne({ method: 'POST', url });
    expect(request.request.body.expiryDate).toBe('2027-10-31');
    request.flush({ ...document, expiryDate: '2027-10-31' });
    await vi.waitFor(() => http.expectOne({ method: 'GET', url }).flush([]));
  });

  it('shows field errors returned by the backend as ProblemDetail', async () => {
    await loadDocuments([]);
    fillValidDocument();
    fill('text', 0, 'x'.repeat(51));

    await submit();
    http.expectOne({ method: 'POST', url }).flush(
      {
        title: 'Bad Request',
        status: 400,
        detail: 'Validation failed',
        errors: [{ field: 'number', message: 'size must be between 0 and 50' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );

    await vi.waitFor(() => expect(errors()).toEqual(['size must be between 0 and 50']));
  });

  it('shows the ProblemDetail detail when the backend rejects the whole request', async () => {
    await loadDocuments([]);
    fillValidDocument();

    await submit();
    http
      .expectOne({ method: 'POST', url })
      .flush(
        { title: 'Not Found', status: 404, detail: 'Vehicle with id 1 not found' },
        { status: 404, statusText: 'Not Found' },
      );

    await vi.waitFor(() => expect(errors()).toEqual(['Vehicle with id 1 not found']));
  });
});
