import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { VehicleReadiness } from './vehicle-readiness';

describe('VehicleReadiness', () => {
  let fixture: ComponentFixture<VehicleReadiness>;
  let element: HTMLElement;
  let http: HttpTestingController;

  const url = '/api/vehicles/1/handover-readiness';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(VehicleReadiness);
    fixture.componentRef.setInput('vehicleId', 1);
    element = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function text(): string {
    return element.textContent!.replace(/\s+/g, ' ').trim();
  }

  function items(): string[] {
    return Array.from(element.querySelectorAll('li'), (item) =>
      item.textContent!.replace(/\s+/g, ' ').trim(),
    );
  }

  it('shows that the vehicle is ready', async () => {
    http
      .expectOne({ method: 'GET', url })
      .flush({ ready: true, missingDocumentTypes: [], problems: [] });
    await fixture.whenStable();

    expect(text()).toContain('Ready for handover');
    expect(items()).toEqual([]);
  });

  it('lists missing documents and problems, including vehicle-level ones', async () => {
    http.expectOne({ method: 'GET', url }).flush({
      ready: false,
      missingDocumentTypes: ['COC'],
      problems: [
        { documentType: 'OC_POLICY', reason: 'EXPIRED' },
        { documentType: 'INVOICE', reason: 'NOT_VERIFIED' },
        { documentType: null, reason: 'NO_HANDOVER_DATE' },
      ],
    });
    await fixture.whenStable();

    expect(text()).toContain('Not ready for handover');
    expect(items()).toEqual([
      'Missing: Certificate of conformity (COC)',
      'OC insurance policy: expired before the handover date',
      'Invoice: not verified',
      'No planned handover date',
    ]);
  });

  it('reloads on demand', async () => {
    http
      .expectOne({ method: 'GET', url })
      .flush({ ready: false, missingDocumentTypes: ['COC'], problems: [] });
    await fixture.whenStable();

    fixture.componentInstance.reload();
    await vi.waitFor(() =>
      http
        .expectOne({ method: 'GET', url })
        .flush({ ready: true, missingDocumentTypes: [], problems: [] }),
    );
    await fixture.whenStable();

    expect(text()).toContain('Ready for handover');
  });

  it('shows a dedicated message when the vehicle does not exist', async () => {
    http
      .expectOne({ method: 'GET', url })
      .flush(
        { title: 'Not Found', status: 404, detail: 'Vehicle with id 1 not found' },
        { status: 404, statusText: 'Not Found' },
      );

    await vi.waitFor(() => expect(text()).toContain('Vehicle not found'));
  });
});
