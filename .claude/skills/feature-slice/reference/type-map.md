# Type map

One row per field kind. "Req" = required, "Opt" = optional.

| Input type | Java (entity / record) | Request validation | SQL column | TS type | Form model / control | List & detail cell |
| --- | --- | --- | --- | --- | --- | --- |
| Short text | `String` | Req: `@NotBlank @Size(max = 50)`; Opt: `@Size(max = 50)` | `VARCHAR(50) NOT NULL` / `VARCHAR(50)` | `string` / `string \| null` | `string` (`''`), `<input type="text">`; Opt → send `value \|\| null` | `{{ x.f }}` / `{{ x.f ?? '—' }}` |
| Long text | `String` | `@Size(max = 500)` (+ `@NotBlank` if Req) | `VARCHAR(500)` | `string` | `<textarea>` | same |
| E-mail | `String` | `@NotBlank @Email @Size(max = 100)` | `VARCHAR(100) NOT NULL` | `string` | `<input type="email">` | same |
| Phone | `String` | `@NotBlank @Pattern(regexp = "\\+?[0-9]( ?[0-9]){8,14}", message = "must be 9 to 15 digits, optionally starting with + and separated by single spaces")` – counts **digits**, spaces only between digits | `VARCHAR(30)` (= longest string the regex accepts: 1 + 15 + 14). Boundaries for tests: 8 digits ✗, 9 ✓, 15 ✓, 16 ✗ | `string` | `<input type="tel">` | same |
| Fixed-format code (VIN, NIP, postal code) | `String` | `@Pattern` with explicit lowercase message, or custom constraint when there is a checksum | `VARCHAR(<exact len>)` | `string` | `<input type="text">` | same |
| Integer | `Integer` | Req: `@NotNull`; range: `@Min/@Max` or custom constraint (`ProductionYear` pattern) | `INTEGER NOT NULL` | `number` / `number \| null` | `number \| null` (`null`), `<input type="number">`; request uses `model.f!` | `{{ x.f }}` |
| Money / decimal | `BigDecimal` | `@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)` | `DECIMAL(12,2)` | `number` | `number \| null`, `<input type="number" step="0.01">` | `{{ x.f }}` |
| Date | `LocalDate` | Req: `@NotNull`; past/future rules: `@PastOrPresent` / `@FutureOrPresent` only if no `Clock` needed, otherwise check in service | `DATE` | `string` (ISO `yyyy-MM-dd`) / `string \| null` | `string` (`''`), `<input type="date">`; Opt → `value \|\| null` | `{{ x.f ?? '—' }}` |
| Boolean flag | `boolean` in entity, `Boolean` in request (null → `false`, use `Boolean.TRUE.equals(...)` in mapper) | none | `BOOLEAN DEFAULT FALSE NOT NULL` | `boolean` | `boolean` (`false`), `<input type="checkbox">` inside `<label>` | `{{ x.f ? 'Yes' : 'No' }}` |
| Enum | `enum XType` + `@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)` | `@NotNull` (bad values → 400 `"must be one of: A, B"` automatically) | `VARCHAR(20) NOT NULL` | string union `'A' \| 'B'` + `X_TYPE_LABELS: Record<XType,string>` + `X_TYPES` | `XType \| ''` (`''`), `<select>` with `<option value="">Select …</option>` | `{{ labels[x.f] }}` |
| Parent reference (nested resource) | `Long parentId` column (`@Column(name = "parent_id", nullable = false)`) – not `@ManyToOne` | taken from the path, **not** from the request body | `BIGINT NOT NULL` + `CONSTRAINT fk_<table>_<parent> FOREIGN KEY (parent_id) REFERENCES <parent>(id) ON DELETE CASCADE` | `parentId: number` in response only | – | – |

Notes
- Default messages to rely on (Hibernate Validator, locale fixed to `en`): `must not be blank`, `must not be null`, `size must be between 0 and 50`, `must be a well-formed email address`, `must be greater than or equal to 0`.
- `@Size` on a required string goes together with `@NotBlank`; order of annotations as in `VehicleRequest` (`@NotBlank` first).
- Column length = the longest value the validation accepts (count separators, `+`, spaces), not the "logical" length.
- Custom format messages name the standard when there is one: `"must be 10 digits with a valid NIP checksum"`-style, `"must be 13 digits with a valid EAN check digit"`.
- Unique field: add `CONSTRAINT uq_<table>_<column> UNIQUE (<column>)` and the repository `existsBy<Field>` / `existsBy<Field>AndIdNot` pair.
