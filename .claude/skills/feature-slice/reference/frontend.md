# Frontend templates

Placeholder: `Widget` (folder `frontend/src/app/widgets/`, files `widget-*.ts`). Same fields as in
`backend.md`. Copy, rename, drop/add fields. No suffixes (`.component`, `.service`), no CSS, no
`standalone: true` (default), `templateUrl` always (no inline templates), UI text in English.

## `widget-api.ts`

```ts
import { HttpClient, httpResource } from '@angular/common/http';
import { Injectable, Signal, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type WidgetKind = 'SMALL' | 'LARGE';

export const WIDGET_KIND_LABELS: Record<WidgetKind, string> = {
  SMALL: 'Small',
  LARGE: 'Large',
};

export const WIDGET_KINDS = Object.keys(WIDGET_KIND_LABELS) as WidgetKind[];

export interface Widget {
  id: number;
  code: string;
  name: string;
  kind: WidgetKind;
  validFrom: string | null;
  active: boolean;
}

export type WidgetRequest = Omit<Widget, 'id'>;

@Injectable({ providedIn: 'root' })
export class WidgetApi {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/widgets';

  list() {
    return httpResource<Widget[]>(() => this.url, { defaultValue: [] });
  }

  get(id: Signal<number | undefined>) {
    return httpResource<Widget>(() => {
      const widgetId = id();
      return widgetId === undefined ? undefined : `${this.url}/${widgetId}`;
    });
  }

  create(request: WidgetRequest): Observable<Widget> {
    return this.http.post<Widget>(this.url, request);
  }

  update(id: number, request: WidgetRequest): Observable<Widget> {
    return this.http.put<Widget>(`${this.url}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
```

## `widget-list.ts` / `widget-list.html`

```ts
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { loadErrorMessage, problemDetailMessage } from '../shared/problem-detail';
import { WIDGET_KIND_LABELS, Widget, WidgetApi } from './widget-api';

@Component({
  selector: 'app-widget-list',
  imports: [RouterLink],
  templateUrl: './widget-list.html',
})
export class WidgetList {
  private readonly widgetApi = inject(WidgetApi);

  protected readonly widgets = this.widgetApi.list();
  protected readonly widgetKindLabels = WIDGET_KIND_LABELS;
  protected readonly deleteError = signal<string | null>(null);

  protected loadError(error: unknown): string {
    return loadErrorMessage(error, 'Widgets not found');
  }

  protected async delete(widget: Widget): Promise<void> {
    this.deleteError.set(null);
    try {
      await firstValueFrom(this.widgetApi.delete(widget.id));
      this.widgets.reload();
    } catch (error) {
      this.deleteError.set(problemDetailMessage(error));
    }
  }
}
```

```html
<h1>Widgets</h1>

<p><a routerLink="/widgets/new">Add widget</a></p>

@if (deleteError(); as message) {
  <p class="error" role="alert">{{ message }}</p>
}

@if (widgets.isLoading()) {
  <p>Loading…</p>
} @else if (widgets.error(); as error) {
  <p class="error" role="alert">{{ loadError(error) }}</p>
} @else if (widgets.value(); as widgets) {
  <table>
    <thead>
      <tr>
        <th>Code</th>
        <th>Name</th>
        <th>Kind</th>
        <th>Valid from</th>
        <th>Active</th>
        <th>Actions</th>
      </tr>
    </thead>
    <tbody>
      @for (widget of widgets; track widget.id) {
        <tr>
          <td>
            <a [routerLink]="['/widgets', widget.id]">{{ widget.code }}</a>
          </td>
          <td>{{ widget.name }}</td>
          <td>{{ widgetKindLabels[widget.kind] }}</td>
          <td>{{ widget.validFrom ?? '—' }}</td>
          <td>{{ widget.active ? 'Yes' : 'No' }}</td>
          <td>
            <a [routerLink]="['/widgets', widget.id, 'edit']">Edit</a>
            <button type="button" (click)="delete(widget)">Delete</button>
          </td>
        </tr>
      } @empty {
        <tr>
          <td colspan="6">No widgets yet.</td>
        </tr>
      }
    </tbody>
  </table>
}
```
`colspan` = number of columns. First column = the unique business key if the entity has one (like VIN), linked to details; otherwise the name. Person-like entities: keep natural order (first name, last name, …) and link the unique key column (e.g. e-mail) – do not reorder fields to put the key first. Detail `<h1>` = the human-readable name.

## `widget-detail.ts` / `widget-detail.html`

```ts
import { Component, inject, input, numberAttribute } from '@angular/core';
import { RouterLink } from '@angular/router';
import { loadErrorMessage } from '../shared/problem-detail';
import { WIDGET_KIND_LABELS, WidgetApi } from './widget-api';

@Component({
  selector: 'app-widget-detail',
  imports: [RouterLink],
  templateUrl: './widget-detail.html',
})
export class WidgetDetail {
  private readonly widgetApi = inject(WidgetApi);

  readonly id = input.required({ transform: numberAttribute });

  protected readonly widget = this.widgetApi.get(this.id);
  protected readonly widgetKindLabels = WIDGET_KIND_LABELS;

  protected loadError(error: unknown): string {
    return loadErrorMessage(error, 'Widget not found');
  }
}
```

```html
<p><a routerLink="/widgets">Back to widgets</a></p>

@if (widget.isLoading()) {
  <p>Loading…</p>
} @else if (widget.error(); as error) {
  <p class="error" role="alert">{{ loadError(error) }}</p>
} @else if (widget.value(); as widget) {
  <h1>{{ widget.name }}</h1>

  <dl>
    <dt>Code</dt>
    <dd>{{ widget.code }}</dd>
    <dt>Kind</dt>
    <dd>{{ widgetKindLabels[widget.kind] }}</dd>
    <dt>Valid from</dt>
    <dd>{{ widget.validFrom ?? '—' }}</dd>
    <dt>Active</dt>
    <dd>{{ widget.active ? 'Yes' : 'No' }}</dd>
  </dl>

  <p><a [routerLink]="['/widgets', widget.id, 'edit']">Edit</a></p>
}
```

## `widget-form.ts` / `widget-form.html`

```ts
import { Component, computed, inject, input, linkedSignal } from '@angular/core';
import { FormField, FormRoot, form, required } from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { loadErrorMessage, problemDetailToFormErrors } from '../shared/problem-detail';
import { optionalNumberAttribute } from '../shared/route-params';
import {
  WIDGET_KINDS,
  WIDGET_KIND_LABELS,
  Widget,
  WidgetApi,
  WidgetKind,
  WidgetRequest,
} from './widget-api';

interface WidgetFormModel {
  code: string;
  name: string;
  kind: WidgetKind | '';
  validFrom: string;
  active: boolean;
}

function toFormModel(widget: Widget | undefined): WidgetFormModel {
  return {
    code: widget?.code ?? '',
    name: widget?.name ?? '',
    kind: widget?.kind ?? '',
    validFrom: widget?.validFrom ?? '',
    active: widget?.active ?? false,
  };
}

function toRequest(model: WidgetFormModel): WidgetRequest {
  return {
    code: model.code,
    name: model.name,
    kind: model.kind as WidgetKind,
    validFrom: model.validFrom || null,
    active: model.active,
  };
}

@Component({
  selector: 'app-widget-form',
  imports: [FormField, FormRoot, RouterLink],
  templateUrl: './widget-form.html',
})
export class WidgetForm {
  private readonly widgetApi = inject(WidgetApi);
  private readonly router = inject(Router);

  readonly id = input(undefined, { transform: optionalNumberAttribute });

  protected readonly isEdit = computed(() => this.id() !== undefined);
  protected readonly widget = this.widgetApi.get(this.id);
  protected readonly widgetKinds = WIDGET_KINDS;
  protected readonly widgetKindLabels = WIDGET_KIND_LABELS;

  private readonly model = linkedSignal(() =>
    toFormModel(this.widget.hasValue() ? this.widget.value() : undefined),
  );

  protected readonly widgetForm = form(
    this.model,
    (path) => {
      required(path.code, { message: 'Code is required' });
      required(path.name, { message: 'Name is required' });
      required(path.kind, { message: 'Kind is required' });
    },
    {
      submission: {
        action: async (widgetForm) => {
          const request = toRequest(widgetForm().value());
          const id = this.id();
          try {
            const saved = await firstValueFrom(
              id === undefined ? this.widgetApi.create(request) : this.widgetApi.update(id, request),
            );
            await this.router.navigate(['/widgets', saved.id]);
            return undefined;
          } catch (error) {
            return problemDetailToFormErrors(error, widgetForm);
          }
        },
      },
    },
  );

  protected loadError(error: unknown): string {
    return loadErrorMessage(error, 'Widget not found');
  }
}
```

```html
<h1>{{ isEdit() ? 'Edit widget' : 'New widget' }}</h1>

@if (widget.isLoading()) {
  <p>Loading…</p>
} @else if (widget.error(); as error) {
  <p class="error" role="alert">{{ loadError(error) }}</p>
} @else if (!isEdit() || widget.hasValue()) {
  <form [formRoot]="widgetForm">
    @for (error of widgetForm().errors(); track error) {
      <p class="error" role="alert">{{ error.message }}</p>
    }

    <label>
      Code
      <input type="text" [formField]="widgetForm.code" />
    </label>
    @if (widgetForm.code().touched()) {
      @for (error of widgetForm.code().errors(); track error) {
        <p class="error" role="alert">{{ error.message }}</p>
      }
    }

    <!-- name: same block with type="text" -->

    <label>
      Kind
      <select [formField]="widgetForm.kind">
        <option value="">Select kind</option>
        @for (kind of widgetKinds; track kind) {
          <option [value]="kind">{{ widgetKindLabels[kind] }}</option>
        }
      </select>
    </label>
    @if (widgetForm.kind().touched()) {
      @for (error of widgetForm.kind().errors(); track error) {
        <p class="error" role="alert">{{ error.message }}</p>
      }
    }

    <label>
      Valid from
      <input type="date" [formField]="widgetForm.validFrom" />
    </label>
    @if (widgetForm.validFrom().touched()) {
      @for (error of widgetForm.validFrom().errors(); track error) {
        <p class="error" role="alert">{{ error.message }}</p>
      }
    }

    <label>
      <input type="checkbox" [formField]="widgetForm.active" />
      Active
    </label>

    <button type="submit" [disabled]="widgetForm().submitting()">Save</button>
    <a [routerLink]="isEdit() ? ['/widgets', id()] : ['/widgets']">Cancel</a>
  </form>
}
```
Every field — required or not — gets the `touched()` + `errors()` block (backend 400s land there), except checkboxes.

## `widgets.routes.ts`, `app.routes.ts`, `app.html`

```ts
import { Routes } from '@angular/router';
import { WidgetDetail } from './widget-detail';
import { WidgetForm } from './widget-form';
import { WidgetList } from './widget-list';

export const widgetsRoutes: Routes = [
  { path: '', component: WidgetList },
  { path: 'new', component: WidgetForm },
  { path: ':id/edit', component: WidgetForm },
  { path: ':id', component: WidgetDetail },
];
```
`app.routes.ts` – append after existing entries (keep the `''` redirect to `vehicles`):
```ts
  {
    path: 'widgets',
    loadChildren: () => import('./widgets/widgets.routes').then((m) => m.widgetsRoutes),
  },
```
`app.html` – append inside `<nav>`: `<a routerLink="/widgets" routerLinkActive="active">Widgets</a>`

## `widget-form.spec.ts` (mandatory cases)

Copy `frontend/src/app/vehicles/vehicle-form.spec.ts` structure verbatim (`fill(type, index, value)`,
`select(value)`, `submit()`, `errors()`, `afterEach(() => http.verify())`) and cover:
1. empty submit → only the `required` messages in field order, `http.expectNone('/api/widgets')`;
2. format/range rules left to the backend → request sent, 400 `errors[]` flushed, messages shown;
3. create → exact request body (optional empty fields as `null`, checkbox default `false`) → navigate `['/widgets', id]`;
4. edit: `setInput('id', '3')`, `detectChanges()`, flush GET, change one field, exact PUT body, navigate;
5. 404 on edit → `'Widget not found'`; 503 → ProblemDetail detail;
6. object-level 400 (`field: null`) shown above the form; 409 detail shown.

Checkbox in tests: `element.querySelector<HTMLInputElement>('input[type="checkbox"]')!.click();`

## `widget-list.spec.ts`

```ts
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { WidgetList } from './widget-list';

describe('WidgetList', () => {
  let fixture: ComponentFixture<WidgetList>;
  let element: HTMLElement;
  let http: HttpTestingController;

  const widget = { id: 1, code: 'w-001', name: 'First widget', kind: 'SMALL', validFrom: null, active: true };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    fixture = TestBed.createComponent(WidgetList);
    element = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function rows(): string[] {
    return Array.from(element.querySelectorAll('tbody tr'), (row) => row.textContent!.trim());
  }

  it('shows the widgets returned by the API', async () => {
    http.expectOne({ method: 'GET', url: '/api/widgets' }).flush([widget]);
    await fixture.whenStable();

    expect(rows()).toHaveLength(1);
    expect(rows()[0]).toContain('w-001');
  });

  it('shows the empty state', async () => {
    http.expectOne({ method: 'GET', url: '/api/widgets' }).flush([]);
    await fixture.whenStable();

    expect(rows()).toEqual(['No widgets yet.']);
  });

  it('deletes a widget and reloads the list', async () => {
    http.expectOne({ method: 'GET', url: '/api/widgets' }).flush([widget]);
    await fixture.whenStable();

    element.querySelector<HTMLButtonElement>('tbody button')!.click();
    http.expectOne({ method: 'DELETE', url: '/api/widgets/1' }).flush(null);
    await vi.waitFor(() => http.expectOne({ method: 'GET', url: '/api/widgets' }).flush([]));
    await fixture.whenStable();

    expect(rows()).toEqual(['No widgets yet.']);
  });

  it('shows the ProblemDetail detail when deleting fails', async () => {
    http.expectOne({ method: 'GET', url: '/api/widgets' }).flush([widget]);
    await fixture.whenStable();

    element.querySelector<HTMLButtonElement>('tbody button')!.click();
    http
      .expectOne({ method: 'DELETE', url: '/api/widgets/1' })
      .flush(
        { title: 'Conflict', status: 409, detail: 'Widget is in use' },
        { status: 409, statusText: 'Conflict' },
      );

    await vi.waitFor(() =>
      expect(element.querySelector('.error')?.textContent?.trim()).toBe('Widget is in use'),
    );
  });
});
```
Never `await fixture.whenStable()` before flushing a pending `httpResource` request – it waits for that request and the test hangs.

---

## Nested variant (child of a parent, e.g. `/api/parents/{parentId}/children`)

No routes and no nav link. Create `child-api.ts` + `child-section.ts/.html` + `child-section.spec.ts`
in the **parent's folder**, and embed `<app-child-section [parentId]="parent.id" />` in the parent's
detail template (inside the parent's `@else if (x.value(); as x)` block).

`child-api.ts`: URL built from the parent id –
```ts
  list(parentId: Signal<number>) {
    return httpResource<Child[]>(() => `/api/parents/${parentId()}/children`, { defaultValue: [] });
  }

  create(parentId: number, request: ChildRequest): Observable<Child> {
    return this.http.post<Child>(`/api/parents/${parentId}/children`, request);
  }
```
`child-section.ts`: `readonly parentId = input.required<number>()`, `children = childApi.list(this.parentId)`,
an inline create form (same signal-form pattern as `WidgetForm`, but on success: `children.reload()` and
reset `model.set(emptyModel())` instead of navigating), `actionError` signal for row actions (like `deleteError`).
Template: `<section><h2>Children</h2>` + table (with `@empty`) + `<h3>Add child</h3>` + form.
Spec: list rendered for the given `parentId`, create posts to the nested URL and reloads, 400/409 shown.
