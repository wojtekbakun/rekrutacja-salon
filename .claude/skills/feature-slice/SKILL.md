---
name: feature-slice
description: Generates a complete, test-covered feature slice (Flyway migration + seed, JPA entity, repository, request/response records, mapper, service, REST controller, backend tests, Angular API service, list/detail/form components, routes, nav link, frontend specs) that matches the existing vehicle module of this Spring Boot + Angular dealership app. Use whenever the user asks to add/generate/scaffold a new entity, CRUD, resource, module, endpoint set or "slice" (e.g. "generate suppliers", "add a CRUD for X with fields …", "nested resource /api/parents/{id}/children").
---

# feature-slice

Produce a slice that is **indistinguishable from the vehicle module** (`backend/.../vehicle/`,
`frontend/src/app/vehicles/`) and fully tested. The templates in `reference/` are the vehicle
module abstracted with a placeholder entity `Widget`; copy them and rename — do not re-invent
structure, names, messages or test style.

## 0. Normalize the input (before writing any file)

Turn the user's description into this table and print it. If something is missing, choose the
default in brackets and list it under "Assumptions" in the final report — do not stop to ask.

| Item | Example | Default |
| --- | --- | --- |
| Entity name (PascalCase singular) | `Supplier` | – (required) |
| Plural / URL segment | `suppliers` → `/api/suppliers` | English plural, kebab-case |
| Table | `supplier` | snake_case singular |
| Parent (nested resource) | `Vehicle` → `/api/vehicles/{vehicleId}/inspections` | none (top-level CRUD) |
| Fields: name, type, required, max length, unique, format, enum values | `contactEmail: String, required, unique (case-insensitive), e-mail format` | String max 50 (100 for e-mail/longer text), required unless stated "optional" |
| Operations | full CRUD | top-level: full CRUD; nested: only what the user lists |

Name rules: Java field camelCase = JSON field = TS field; SQL column snake_case; labels in English sentence case (`firstName` → "First name").

## 1. Read before writing

1. `reference/type-map.md` – field type → Java / validation / SQL / TS / form control / list cell.
2. `reference/backend.md` – backend templates (top-level and nested variant).
3. `reference/backend-tests.md` – mandatory backend test matrix + templates.
4. `reference/frontend.md` – frontend templates (+ nested section variant).
5. `reference/pitfalls.md` – mistakes seen when generating slices in this repo. **Read it.**
6. Open the real vehicle file next to each template only if something is unclear.

Find the next migration number: `ls backend/src/main/resources/db/migration` → highest `V<n>` → use `n+1` (schema) and `n+2` (seed).

## 2. Files to create / modify

Backend (`backend/src/main/java/com/example/dealership/<feature>/`, `<feature>` = lowercase singular, e.g. `supplier`):
- [ ] `X.java` entity, `XRepository.java`, `XRequest.java`, `XResponse.java`, `XMapper.java`, `XService.java`, `XController.java`
- [ ] enum per enum field (`XType.java`); custom constraint pair (`Ean13.java` + `Ean13Validator.java`) per non-trivial format/cross-field rule
- [ ] `backend/src/main/resources/db/migration/V<n>__<table>.sql`, `V<n+1>__<table>_seed.sql` (2–3 rows that **pass all validation rules**)
- [ ] tests in `backend/src/test/java/com/example/dealership/<feature>/`: `XServiceTest`, `XControllerTest`, `XRepositoryTest` (+ `XValidatorTest` per custom validator)

Frontend (`frontend/src/app/<plural>/`, top-level resource):
- [ ] `x-api.ts`, `x-list.ts` + `.html`, `x-detail.ts` + `.html`, `x-form.ts` + `.html`, `<plural>.routes.ts`
- [ ] `x-form.spec.ts`, `x-list.spec.ts`
- [ ] modify `app.routes.ts` (lazy `loadChildren`) and `app.html` (nav link)

Nested resource (has a parent): backend as above with the nested variant; frontend = `x-api.ts` +
`x-section.ts/.html` + `x-section.spec.ts` embedded in the parent's detail page (no routes, no nav link).
Do not change the parent's API or entity unless the user asked.

## 3. Generate

Order: migration → entity (must match migration exactly) → enum/validators → repository → request/response → mapper → service → controller → backend tests → frontend api → components → routes/nav → specs.

Rules that matter most (details in references):
- Uniqueness: check in the service **before** save (`existsBy…` on create, `existsBy…AndIdNot` on update after loading the entity) → `ConflictException("<Entity> with <field label> <value> already exists")`; also a `UNIQUE` constraint in SQL. Case-insensitive unique (e-mail, codes): normalize in the mapper (`trim` + `toLowerCase(Locale.ROOT)`) so the DB constraint enforces it as well.
- Messages: English, lowercase Bean Validation style; custom `message` only when the default is unclear.
- Optional fields: no `@NotNull`, nullable column, TS `T | null`, form sends `null` for empty input.
- Frontend validates only `required`; everything else comes back as a 400 `ProblemDetail` and is shown by `problemDetailToFormErrors`.
- Do not add dependencies, Lombok, CSS, or new shared helpers; reuse `error/*` and `shared/*`.

## 4. Verify (mandatory, loop until green)

```bash
docker exec salon-dev bash -lc 'cd /workspace && ./test.sh'
```
(Without the container: `./test.sh` from the repo root.) If a test fails, fix the **generated code** (never weaken an assertion or the vehicle tests). Then smoke-test the API if the app can be started:
`curl -i localhost:8080/api/<plural>` → 200; POST valid → 201 + `Location`; POST duplicate → 409; POST invalid → 400 with `errors`.

## 5. Final checklist and report

- [ ] Migration numbers continue the sequence; no existing migration edited
- [ ] Entity ↔ migration: same columns, nullability, lengths, enum `VARCHAR(20)`
- [ ] Every request field has validation matching the input table; unique fields → 409 on create AND update
- [ ] Controller: 201 + `Location`, 204 on DELETE, 404 ProblemDetail `"<Entity> with id N not found"`
- [ ] Backend test matrix from `reference/backend-tests.md` fully covered (incl. 400 `errors` order, 409 create/update, repository `exists…`)
- [ ] Frontend: api/list/detail/form/routes/nav (or section for nested), only `required` validators, specs pass
- [ ] `./test.sh` green
- [ ] Report: files created/modified, the normalized input table, **Assumptions** (every default you chose), anything you could not do
