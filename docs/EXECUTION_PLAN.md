# Plan wykonania zadania rekrutacyjnego (instrukcja dla nowej sesji)

Ten plik jest instrukcją dla sesji Claude Code, która wykona zadanie z `BRIEF.md`. Zawiera to, co ustaliła sesja przygotowawcza, żeby nie odkrywać tego drugi raz. Źródłem wymagań jest `BRIEF.md`; tam, gdzie ten plan i brief się różnią, wygrywa brief.

---

## 0. Zanim zaczniesz

1. Przeczytaj w całości: `BRIEF.md`, `INTENT.md`, ten plik. Nie czytaj od nowa całego modułu pojazdów, jego konwencje są spisane w sekcji 3; otwieraj konkretne pliki, gdy ich potrzebujesz jako wzorca.
2. **`INTENT.md` czeka na akceptację użytkownika.** Zapytaj, czy jest zatwierdzony i czy ma uwagi. Bez akceptacji nie implementuj reguł z sekcji 5 i 6 `INTENT.md`.
3. Sprawdź środowisko:
   ```bash
   docker ps --filter name=salon-dev          # kontener powinien działać
   docker compose -f docker-compose.dev.yml up -d   # jeśli nie działa
   docker exec salon-dev bash -lc 'cd /workspace && ./test.sh'   # musi przejść przed zmianami
   ```
4. **Najpierw plan, potem kod.** Pracuj w trybie planowania (plan mode): zanim zmienisz jakikolwiek plik, przygotuj własny plan wykonania na podstawie tego dokumentu i pokaż go użytkownikowi do akceptacji. Plan ma zawierać: fazy z sekcji 4 rozpisane na konkretne kroki i commity, listę plików do utworzenia/zmiany w każdej fazie, sposób sprawdzenia każdej bramki, ryzyka i otwarte pytania (w tym akceptację `INTENT.md`). Dopiero po akceptacji wyjdź z trybu planowania.
5. **Plan także przed każdą fazą.** Na początku każdej fazy z sekcji 4 przedstaw krótki plan tej fazy (pliki, testy, commity) i poczekaj na akceptację, zanim zaczniesz ją implementować. Jeśli w trakcie fazy plan okaże się błędny, zatrzymaj się i pokaż poprawiony plan zamiast improwizować.
6. Utwórz listę zadań z zaakceptowanego planu i prowadź ją przez całą sesję.

## 1. Zasady pracy (obowiązują całą sesję)

| Zasada | Szczegóły |
| --- | --- |
| **Push tylko po zatwierdzeniu** | Commituj lokalnie po każdym kroku. `git push` wyłącznie, gdy użytkownik wprost na to pozwoli. Repo `origin` jest publiczne. |
| **Commity małe i częste** | Brief ocenia historię git. Jeden commit = jeden logiczny krok, opis po angielsku w trybie rozkazującym (jak w historii startera). Stopka `Co-Authored-By`. |
| **Wszystko buduje się w kontenerze** | JDK 21 i Node 22 są tylko w kontenerze `salon-dev` (folder repo podmontowany jako `/workspace`). Komendy: `docker exec salon-dev bash -lc 'cd /workspace/backend && ./mvnw -q test'`, `docker exec salon-dev bash -lc 'cd /workspace/frontend && npm test'`, całość: `./test.sh`. |
| **Zielone testy po każdej fazie** | `./test.sh` musi przejść przed każdym commitem kończącym fazę. Czerwone testy startera = regresja, napraw przed pójściem dalej. |
| **DECISIONS.md na bieżąco** | Każda pomyłka AI, sposób jej wykrycia i ręczna poprawka trafia do `DECISIONS.md` od razu, nie na końcu. To jest oceniany produkt. |
| **Logi sesji** | Hook w `.claude/settings.json` zapisuje log do `ai-logs/` po każdej odpowiedzi. Dodawaj `ai-logs/` do commitów. Brief chce „pełnych logów bez wycinania”: przed oddaniem zapytaj użytkownika, czy dołączyć surowe pliki z `~/.claude/projects/-Users-wojtek-Documents-projects-rekrutacja/` (zawierają jego e-mail). |
| **Kontrakt API jest święty** | Ścieżki, nazwy pól, enumy i kody 409 z `BRIEF.md` są testowane automatycznie przez rekruterów. Nie zmieniaj ich nazw ani kształtu. |
| **Czas** | Brief daje 2 h. Orientacyjny podział w sekcji 4. Jeśli faza przekracza budżet o >50%, zatrzymaj się i zapytaj użytkownika o priorytety. |

## 2. Stan na start

- `main` lokalnie: dev container, hook logów, scalony starter (`upstream` = `HouseOfAngular/vehicle-handover-starter`).
- `INTENT.md` napisany, **niezacommitowany**, czeka na akceptację.
- Artefakt z wymaganiami: https://claude.ai/artifact/Hxqg2DQYzpm3vwNXnh9PhJ
- `./test.sh` startera przechodzi w kontenerze.
- Kontener ma Node 22.23 (wymagane ≥ 22.22.3, `.nvmrc` mówi 24 – oba są wspierane przez `check-node.sh`).

## 3. Konwencje modułu pojazdów (wzorzec dla wszystkiego)

### Backend (`backend/src/main/java/com/example/dealership/<feature>/`)
- Pakiet per feature: `Entity`, `Repository`, `Request` (record + Bean Validation), `Response` (record), `Mapper` (final class, statyczne `toEntity`, `updateEntity`, `toResponse`), `Service`, `Controller`. Wspólne błędy w pakiecie `error`.
- Encja JPA: `@Entity @Table(name="…")`, `@GeneratedValue(IDENTITY)`, `protected` konstruktor bezargumentowy + publiczny z polami, gettery/settery, bez Lomboka. Enumy `@Enumerated(STRING)` z `length`.
- Serwis: `@Service @Transactional(readOnly = true)` na klasie, `@Transactional` na metodach zapisu. Unikalność sprawdzana w serwisie przez `existsByX` / `existsByXAndIdNot` → `ConflictException` (409). Brak rekordu → `NotFoundException` z komunikatem `"<Entity> with id N not found"`. Lista sortowana `Sort.by("id")`.
- Kontroler: `@RequestMapping("/api/<plural>")`, `@Valid @RequestBody`, POST zwraca 201 + `Location` (`ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")`), DELETE 204.
- Błędy: `GlobalExceptionHandler` zwraca `ProblemDetail`; 400 ma właściwość `errors: [{field, message}]` posortowaną po polu. Komunikaty walidacji po angielsku, małą literą (np. `"must be between 1990 and next year"`). Złe wartości enum obsługuje już handler.
- Własna walidacja = adnotacja + `ConstraintValidator` (wzór: `ProductionYear`).
- Baza: H2 in-memory, Flyway, `ddl-auto=validate` – **encja musi zgadzać się z migracją**. Nowe migracje `V<n>__<opis>.sql` z kolejnym numerem; **nigdy nie edytuj istniejących migracji** (V3 pokazuje wzór dodania kolumny NOT NULL: dodaj, wypełnij, ustaw NOT NULL). Seed w osobnej migracji.
- Testy (JUnit 5, AssertJ, Mockito):
  - `<X>ServiceTest` – `@ExtendWith(MockitoExtension.class)`, mock repozytorium, przypadki: lista, 404, create, 409 na create, update, 409 na update, delete.
  - `<X>ControllerTest` – `@WebMvcTest`, `@MockitoBean` serwis, `jsonPath`, kody 200/201+Location/204/400 z `errors`/404/409.
  - `<X>RepositoryTest` – `@DataJpaTest`, zapis i odczyt + zapytania `exists…`.

### Frontend (`frontend/src/app/<feature>/`)
- Angular 22, standalone, **bez sufiksów** (`vehicle-list.ts`, klasa `VehicleList`), osobny plik `.html`, brak własnych CSS (tylko klasa `.error`).
- `<x>-api.ts`: interfejsy, `type XRequest = Omit<X,'id'>`, etykiety enumów jako `Record<Enum,string>` + tablica wartości, `@Injectable({providedIn:'root'})`, odczyty przez `httpResource`, zapisy przez `HttpClient` zwracające `Observable`.
- Formularz: **signal forms** (`@angular/forms/signals`: `form`, `required`, `FormField`, `FormRoot`), `linkedSignal` z modelu, w `submission.action` zapis przez `firstValueFrom`, błędy z backendu przez `problemDetailToFormErrors`. Frontend sprawdza tylko `required`; formaty i zakresy waliduje backend.
- Lista/szczegóły: stany `isLoading` / `error` (`loadErrorMessage(err, '<X> not found')`) / `value`, `@empty` w tabeli, usuwanie z `deleteError` sygnałem.
- Routing: `<x>.routes.ts` (`''`, `'new'`, `':id/edit'`, `':id'`), lazy `loadChildren` w `app.routes.ts`, link w `app.html` (`routerLinkActive="active"`). Parametry trasy jako `input` (`withComponentInputBinding`).
- UI i komunikaty po angielsku.
- Testy: Vitest + `TestBed`, `provideHttpClientTesting`, `HttpTestingController` (`expectOne`, `flush`, `http.verify()`), `vi.waitFor`. Wzór: `vehicle-form.spec.ts` (required, 400 z polami, 409, create→navigate, edit, 404).

## 4. Fazy (kolejność wymagana przez brief)

Budżet orientacyjny w nawiasach (razem ~2 h).

### Faza 1 – `CLAUDE.md` (15 min)
Kontekst dla agenta w katalogu głównym. Zawartość:
- Czym jest projekt, jak uruchomić i testować (komendy przez kontener, `./test.sh`).
- Konwencje z sekcji 3 w formie krótkich reguł (nie esej): struktura pakietów, nazewnictwo, błędy/ProblemDetail, migracje, wzorzec testów backend i frontend.
- Twarde zakazy: nie edytować istniejących migracji, nie zmieniać kontraktu API, nie dodawać zależności bez potrzeby, komunikaty po angielsku.
- Wskazanie na `INTENT.md` (wymagania) i `DECISIONS.md` (dziennik decyzji).
- Definicja „gotowe”: kompiluje się, `./test.sh` zielony, testy dla każdej reguły.

Bramka: `CLAUDE.md` < ~150 linii, każda reguła sprawdzalna w kodzie. Commit.

### Faza 2 – skill `feature-slice` (35 min)
Lokalizacja: `.claude/skills/feature-slice/SKILL.md` + pliki pomocnicze. Format Agent Skills: frontmatter YAML z `name: feature-slice` i `description` (kiedy użyć: „wygeneruj CRUD / wycinek / moduł dla encji…”; opis decyduje o tym, czy agent w ogóle sięgnie po skill).

**Najważniejsze:** rekruterzy uruchomią skill na nieznanej encji i porównają z agentem bez skilla. Skill ma więc zawierać to, czego agent bez niego nie zgadnie albo zrobi niespójnie. Treść:
1. **Wejście**: jak opisać encję (nazwa, pola z typem, wymagalność, ograniczenia, unikalność, enumy, opcjonalnie encja nadrzędna dla zasobu zagnieżdżonego typu `/api/parents/{id}/children`). Jeśli opis jest niepełny – przyjmij rozsądne domyślne i wypisz je na końcu.
2. **Mapa typów**: typ z opisu → Java + Bean Validation → SQL → TypeScript → kontrolka formularza → kolumna listy.
3. **Lista plików do utworzenia i zmodyfikowania** (dokładne ścieżki, backend + frontend + testy + migracje + `app.routes.ts` + `app.html`), z numeracją migracji: „znajdź najwyższe `V<n>` i użyj `n+1`”.
4. **Szablony** w plikach pomocniczych (`templates/…` albo `reference.md`) wyprowadzone z modułu pojazdów, z placeholderami. Lepiej odesłać do konkretnych plików pojazdów jako wzorca i spisać różnice niż kopiować cały kod.
5. **Macierz testów obowiązkowych** (sekcja 3) – skill ma generować wszystkie, także 409 dla pól unikalnych i 400 z `errors`.
6. **Pułapki**: `ddl-auto=validate` (encja = migracja), nie edytować starych migracji, enum jako `VARCHAR` + `@Enumerated(STRING)`, unikalność w serwisie + constraint w DB, `existsByXAndIdNot` przy update, porównanie bez wielkości liter tam, gdzie to ma sens (e-mail), tylko `required` na froncie, komunikaty po angielsku.
7. **Weryfikacja końcowa**: uruchom `./test.sh` (przez kontener, jeśli jest), popraw do zielonego, wypisz utworzone pliki i przyjęte założenia.

Bramka: skill ma checklistę, którą da się odhaczyć; nie zawiera kodu specyficznego dla klientów ani dokumentów. Commit.

### Faza 3 – Feature B: klienci, **wyłącznie skillem** (25 min)
- Uruchom skill w **świeżym subagencie** (bez kontekstu tej rozmowy), podając tylko opis encji. Tak zasymulujesz ocenę rekruterów i sprawdzisz skill, a nie swoją wiedzę z sesji. Opis encji weź z `INTENT.md` B1–B3 i założeń Z17–Z18 (e-mail unikalny bez rozróżniania wielkości liter, NIP opcjonalny 10 cyfr z sumą kontrolną, telefon).
- Przejrzyj wynik względem sekcji 3. Jeśli coś jest źle: **popraw skill i wygeneruj ponownie** (cofnij wygenerowane pliki), nie poprawiaj kodu ręcznie. Każdą iterację skilla i każdą konieczną ręczną poprawkę wpisz do `DECISIONS.md`.
- Bramka: `./test.sh` zielony, `/api/customers` CRUD działa, 409 na duplikat e-maila. Osobne commity: poprawki skilla, wygenerowany feature.

### Faza 4 – Feature A: dokumenty i gotowość (40 min)
Kolejne commity:
1. **`dealerRegistration` na pojeździe** (ręcznie): nowa migracja `ALTER TABLE vehicle ADD COLUMN dealer_registration BOOLEAN DEFAULT FALSE NOT NULL`; pole w encji, `VehicleRequest` jako opcjonalny `Boolean` (brak = `false`, Z1), `VehicleResponse`, mapper, frontend (`Vehicle`, formularz z checkboxem, szczegóły, spec). Istniejące testy pojazdów muszą nadal przejść (zaktualizuj oczekiwane body w `vehicle-form.spec.ts`).
2. **Szkielet dokumentów skillem**: encja `VehicleDocument` (tabela np. `vehicle_document`, FK do `vehicle` z `ON DELETE CASCADE` – Z16), pola `type`, `number`, `issueDate`, `expiryDate`, `status`, zasób zagnieżdżony pod pojazdem. Odnotuj w `DECISIONS.md`, co wygenerował skill.
3. **Reguły biznesowe ręcznie**, każda z testami jednostkowymi serwisu:
   - nowy dokument zawsze `PENDING` (status nie przychodzi w żądaniu),
   - `issueDate` nie z przyszłości (wstrzykiwany `java.time.Clock` jako bean, Z7), `expiryDate >= issueDate` → 400 z `errors`,
   - jeden aktywny (nie `REJECTED`) dokument danego typu → 409,
   - przejścia statusów wg macierzy z `INTENT.md` A7 w **jednym miejscu** (np. metoda na enumie `DocumentStatus.canTransitionTo`) → 409 dla niedozwolonych,
   - dokument innego pojazdu → 404.
4. **Endpoint gotowości** `GET /api/vehicles/{vehicleId}/handover-readiness` wg `INTENT.md` A9 i założeń Z8–Z12. Logika w czystej klasie (np. `HandoverReadinessCalculator`) testowanej bez Springa; testy graniczne: wygaśnięcie w dniu wydania (OK), dzień wcześniej (`EXPIRED`), brak daty wydania, tylko odrzucony dokument (= brak), `dealerRegistration` true/false, PENDING i przeterminowany (dwa problemy). Kształt JSON dokładnie jak w briefie (`documentType: null` musi być serializowane, nie pomijane).
5. **UI**: sekcja „Documents” w `vehicle-detail.html`: lista, formularz dodania, przyciski Verify/Reject tylko dla dozwolonych przejść, blok gotowości (Ready / missing / problems). Spec dla nowego komponentu.

Bramka: `./test.sh` zielony; ręczny smoke test API (`curl` w kontenerze) dla każdego kryterium z `INTENT.md` sekcja 8.

### Faza 5 – `DECISIONS.md` i domknięcie (15 min)
- Struktura: (1) co wygenerował skill vs co ręcznie – dla B i A osobno, (2) gdzie AI się pomyliło, jak to wykryto (test, review, uruchomienie), co poprawiono, (3) decyzje projektowe z uzasadnieniem i odwołaniem do `INTENT.md`.
- Przejrzyj diff całości (`git diff upstream/main --stat`), uruchom `./test.sh`, sprawdź że kontrakt API zgadza się co do znaku.
- Zapytaj użytkownika o zgodę na push i o surowe logi. Dopiero wtedy `git push`.

## 5. Gotowy prompt startowy dla nowej sesji

Uruchom nową sesję **w trybie planowania** (plan mode), żeby nie mogła zmieniać plików przed akceptacją planu, i wklej:

> Wykonujemy zadanie rekrutacyjne z `BRIEF.md` w tym repo. Przeczytaj `BRIEF.md`, `INTENT.md` i `docs/EXECUTION_PLAN.md`. Zanim cokolwiek zmienisz, przygotuj plan wykonania zgodnie z sekcją 0 punkt 4 i przedstaw mi go do akceptacji. Po akceptacji realizuj go faza po fazie; przed każdą fazą pokaż jej krótki plan i poczekaj na moją zgodę. Builduj i testuj w kontenerze `salon-dev`. Commituj po każdym kroku, ale nie pushuj bez mojej zgody.

Uwaga: nowa sesja powinna pracować w tym samym folderze (nie w nowym worktree), bo `INTENT.md` i ten plik nie są jeszcze zacommitowane.
