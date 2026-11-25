# Backend tests

Mirror `VehicleServiceTest`, `VehicleControllerTest`, `VehicleRepositoryTest` (same imports, same
naming `methodDoesWhatWhenCondition`, constants for test data, private static factory methods
`widget(...)`, `request(...)`, `response()` at the bottom).

## Mandatory matrix (one test per row – none optional)

| Class | Test | Asserts |
| --- | --- | --- |
| ServiceTest | `findAllReturnsMappedWidgets` | `findAll(Sort.by("id"))` stubbed, mapped values in order |
| ServiceTest | `findByIdThrowsNotFoundWhenWidgetDoesNotExist` | `NotFoundException`, exact message `"Widget with id 99 not found"` |
| ServiceTest | `createSavesWidget` | `save` answers its argument, response fields (incl. defaults like `active=false`, normalized values) |
| ServiceTest | `createThrowsConflictWhen<Field>AlreadyExists` (each unique field) | `ConflictException` + exact message, `verify(repo, never()).save(any())` |
| ServiceTest | `create…` normalization case (only if normalized) | request `"W-001 "` → `existsByCode("w-001")` checked, stored `"w-001"` |
| ServiceTest | `updateChangesExistingWidget` | entity mutated, `verify(repo).save(entity)` |
| ServiceTest | `updateThrowsConflictWhen<Field>BelongsToAnotherWidget` | `existsBy…AndIdNot` → 409, never saves |
| ServiceTest | `updateThrowsNotFoundWhenWidgetDoesNotExist` | 404 message |
| ServiceTest | `deleteRemovesExistingWidget` / `deleteThrowsNotFoundWhenWidgetDoesNotExist` | `verify(repo).delete(entity)` / 404 |
| ControllerTest | `findAllReturnsWidgets` | 200, `$.length()`, every field incl. enum string and ISO date |
| ControllerTest | `findByIdReturnsWidget` / `findByIdReturnsNotFoundProblemWhenWidgetDoesNotExist` | 200 / 404 + `APPLICATION_PROBLEM_JSON` + `$.status` + `$.detail` |
| ControllerTest | `createReturnsCreatedWidget` | 201, `header().string("Location", "http://localhost/api/widgets/1")`, `$.id` |
| ControllerTest | `createReturnsBadRequestProblemWithFieldErrorsWhenRequestIsInvalid` | 400, `$.detail = "Validation failed"`, `$.errors.length()`, each `errors[i].field` **sorted alphabetically** + messages, `verifyNoInteractions(service)` |
| ControllerTest | one 400 test per custom format/constraint (e-mail, phone, checksum, …) | exact field + message |
| ControllerTest | `createReturnsBadRequestProblemWithFieldErrorWhen<Enum>IsUnknown` (if enum) | `"must be one of: SMALL, LARGE"` |
| ControllerTest | `createReturnsCreatedWidgetWhenOptionalFieldsAreMissing` (if optional fields) | 201 with optional fields omitted from body |
| ControllerTest | `createReturnsConflictProblemWhen<Field>AlreadyExists` | 409 + detail |
| ControllerTest | `updateReturnsUpdatedWidget`, `updateReturnsNotFoundProblem…`, `updateReturnsBadRequestProblem…`, `updateReturnsConflictProblem…` | 200 / 404 / 400 / 409 |
| ControllerTest | `deleteReturnsNoContent` / `deleteReturnsNotFoundProblemWhenWidgetDoesNotExist` | 204 + `verify(service).delete(1L)` / 404 |
| RepositoryTest | `saveStoresWidgetThatCanBeReadBack` | save, `flush`, `clear`, `findById` → `isNotSameAs`, every field equal |
| RepositoryTest | `existsBy<Field>AndIdNotFinds<Field>OfAnotherWidget` (each unique field) | true for other row, false for itself, false for unknown value |
| RepositoryTest | `saveRejectsDuplicate<Field>` (each unique field) | `assertThatThrownBy(() -> repo.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class)` (use `saveAndFlush` – `entityManager.flush()` throws an untranslated Hibernate exception); proves the SQL constraint exists |
| ValidatorTest (per custom constraint) | valid values, invalid values, `null` → valid | plain JUnit, `new XValidator().isValid(value, null)` |

Nested variant adds: service 404 when parent is missing (message of the parent), 404 when the child belongs to another parent (`findByIdAndParentId` empty), list filtered by parent; controller paths with `{parentId}`, `Location` `http://localhost/api/parents/1/children/5`.

## ControllerTest skeleton

```java
@WebMvcTest(WidgetController.class)
class WidgetControllerTest {

    private static final String VALID_BODY = """
            {
              "code": "w-001",
              "name": "First widget",
              "kind": "SMALL",
              "validFrom": "2026-01-15",
              "active": true
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WidgetService widgetService;

    @Test
    void createReturnsBadRequestProblemWithFieldErrorsWhenRequestIsInvalid() throws Exception {
        String body = """
                { "code": "", "name": "%s", "kind": "SMALL" }
                """.formatted("x".repeat(51));

        mockMvc.perform(post("/api/widgets").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[1].field").value("name"))
                .andExpect(jsonPath("$.errors[1].message").value("size must be between 0 and 50"));
        verifyNoInteractions(widgetService);
    }
    // … rest of the matrix
}
```

Pitfalls in tests
- One field can produce two errors (e.g. `""` violates `@NotBlank` and `@Pattern`) → errors are sorted by field, then message; prefer invalid values that break exactly one rule, or assert both.
- `@WebMvcTest` loads only the controller + advice: a service that needs a `Clock` bean is mocked, so no extra config is needed there; `@DataJpaTest` does not load services.
- Do not use `LocalDate.now()` in assertions of date rules – use a fixed `Clock` (`Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC)`).
- Never edit or weaken vehicle tests to make a new slice pass.
