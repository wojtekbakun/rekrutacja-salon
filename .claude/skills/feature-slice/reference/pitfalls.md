# Pitfalls (check each one before reporting done)

## Database / JPA
- `spring.jpa.hibernate.ddl-auto=validate`: a column missing in SQL, a different type (`INTEGER` vs `BIGINT`, `VARCHAR` vs `BOOLEAN`) or a different enum length fails **every** Spring test with "Schema-validation". Lengths in `@Column(length)` must equal `VARCHAR(n)`.
- Never edit `V1…Vn` that already exist – Flyway checksums. New change = new file with the next number. Two new files in one slice: schema `n+1`, seed `n+2`.
- Java `boolean` field ↔ `BOOLEAN DEFAULT FALSE NOT NULL`. Java `LocalDate` ↔ `DATE`. Enum ↔ `VARCHAR(20)` + `@Enumerated(EnumType.STRING)` (never ORDINAL).
- Column names: Hibernate maps `validFrom` → `valid_from` automatically; do not add `@Column(name=…)` unless the name differs.
- Seed rows must be valid according to the request validation (checksums, formats, lowercase normalized values), otherwise editing a seeded row in the UI fails with 400.
- Nested resources: FK with `ON DELETE CASCADE`, otherwise deleting the parent through its existing `DELETE` endpoint starts returning 409.

## Validation and errors
- Bean Validation messages come from Hibernate Validator in English (locale is fixed). Do not hand-write `"must not be blank"` as a custom message – just use the annotation.
- Custom messages: lowercase, no trailing period, describe the rule (`"must be 13 digits with a valid check digit"`).
- Uniqueness: service check (409 with message) **and** DB constraint (the `DataIntegrityViolationException` handler is only a race safety net). On update, load the entity first (404 wins over 409), then `existsBy…AndIdNot`.
- Case-insensitive uniqueness: normalize before both the exists-check and the save (mapper `normalize…`), test it with a mixed-case duplicate.
- Optional numbers/dates: no `@NotNull`; optional strings: `@Size` only. Empty string from the form is sent as `null`.
- Do not add a new exception type or handler for a 400/404/409 that `error/*` already covers.
- Rules that depend on "today": inject `java.time.Clock` (a `Clock` bean, `Clock.systemDefaultZone()`, registered once in a `@Configuration`), never `LocalDate.now()` without a clock – otherwise tests are date-dependent.

## REST
- `POST` → `201` + `Location` from `ServletUriComponentsBuilder.fromCurrentRequest()`; `DELETE` → `204`; `PUT` → `200` with body.
- 404 message format is fixed: `"<Entity> with id <id> not found"`.
- Do not generate endpoints the user did not ask for when a contract lists them explicitly (nested resources especially). Do not add paging, filtering or search.
- JSON field names = Java record component names = TS interface fields. Response nulls are serialized (Jackson default) – keep it; contracts may rely on `"field": null`.

## Frontend
- Only `required` validators on the client; e-mail/phone/length checks come from the backend 400.
- Required-message text: `"<Label> is required"` in the order fields appear in the form – the form spec asserts this order.
- `select` starts with an empty option `Select <label lowercase>`; model type `Enum | ''`.
- `httpResource` for reads, `HttpClient` + `firstValueFrom` for writes; never subscribe manually.
- Do not add `styleUrl`/`styles`, `standalone: true`, `ChangeDetectionStrategy`, constructor injection – follow the vehicle files exactly (`inject()`).
- Register the lazy route AND the nav link; keep the root redirect to `vehicles`.
- In specs never `await fixture.whenStable()` while an `httpResource` request is unflushed.

## Process
- Run `./test.sh` (in container `salon-dev` if present) and fix until green. Do not modify vehicle tests or shared code to make the new slice pass – if that seems necessary, stop and report it.
- Report every default you chose as an assumption.
