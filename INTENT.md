# INTENT: dokumenty pojazdu, gotowość do wydania, klienci

Źródło: transkrypcja spotkania z salonem Auto Mazur (`BRIEF.md`, sekcja „Transkrypcja spotkania”) oraz stały kontrakt API z briefu. Odwołania `[T:nn]` wskazują linię w `BRIEF.md`, `[K]` – kontrakt API z briefu, `[KOD]` – stan obecnego kodu.

Zasada: kontrakt API jest nadrzędny wobec transkrypcji. Gdzie transkrypcja jest sprzeczna lub milczy, przyjmuję założenie (sekcja 5) i zapisuję pytanie do klienta (sekcja 6).

---

## 1. Problem biznesowy

Salon sprawdza komplet dokumentów przed wydaniem auta na papierowej checkliście; zdarzyło się wydanie bez COC.

> **Anna:** „Każde auto przed wydaniem musi mieć komplet dokumentów, a dziś sprawdzamy to na wydrukowanej checkliście. W zeszłym miesiącu klient przyjechał po auto i okazało się, że nie ma COC, bo ktoś przeoczył.” `[T:67]`

Cel: system sam liczy, czy auto jest gotowe do wydania, i pokazuje, czego brakuje.

---

## 2. Wymagania funkcjonalne – Feature A: dokumenty pojazdu i gotowość

### A1. Typy dokumentów
`SALES_AGREEMENT` (umowa sprzedaży), `INVOICE` (faktura), `COC` (świadectwo zgodności), `OC_POLICY` (polisa OC), `REGISTRATION_POA` (pełnomocnictwo do rejestracji). `[K]`

> **Anna:** „Umowa sprzedaży, faktura, świadectwo zgodności, czyli COC, i polisa OC. Bez tych czterech auto nie wyjeżdża. Do tego pełnomocnictwo do rejestracji […]” `[T:71]`

### A2. Komplet wymagany zawsze: 4 dokumenty
`SALES_AGREEMENT`, `INVOICE`, `COC`, `OC_POLICY` są wymagane dla każdego auta. `[T:71]`

### A3. Pełnomocnictwo wymagane warunkowo
`REGISTRATION_POA` jest wymagane tylko, gdy salon rejestruje auto za klienta. Domyślnie klient rejestruje sam. Doradca zaznacza to przy aucie.

> **Anna:** „[…] ono jest potrzebne tylko wtedy, kiedy to my rejestrujemy auto za klienta. Część klientów rejestruje sama.” `[T:71]`
> **Anna:** „Tomek może to zaznaczać przy aucie, jak ustali z klientem. Domyślnie klient rejestruje sam, więc pełnomocnictwo jest wymagane tylko przy autach, gdzie Tomek to zaznaczy.” `[T:81]`

**Konsekwencja w kodzie:** encja `Vehicle` dostaje nowe pole logiczne `dealerRegistration` (domyślnie `false`), edytowalne w formularzu pojazdu. Dziś takiego pola nie ma `[KOD: Vehicle.java]`.

### A4. Dodawanie dokumentu
`POST /api/vehicles/{vehicleId}/documents` z polami `type`, `number`, `issueDate`, opcjonalnie `expiryDate`. Nowy dokument ma status `PENDING`. `[K]`

> **Anna:** „Jak ktoś wgra skan, dokument czeka na weryfikację.” `[T:85]`

### A5. Walidacja dat
- `issueDate` – wymagana, **nie może być z przyszłości** (dzisiaj jest dozwolone).
  > **Anna:** „Nie może być z przyszłości. To by znaczyło, że ktoś się pomylił przy wpisywaniu.” `[T:95]`
- `expiryDate` – opcjonalna; nie każdy dokument ją ma.
  > **Anna:** „Nie każdy. Umowa i faktura nie mają. Polisa ma, pełnomocnictwo zwykle też.” `[T:91]`

Błąd walidacji → 400 w formacie `ProblemDetail` z listą `errors`, jak w module pojazdów `[KOD: GlobalExceptionHandler]`.

### A6. Jeden aktywny dokument danego typu
Na auto może być tylko jeden **aktywny** (nieodrzucony, czyli `PENDING` lub `VERIFIED`) dokument danego typu. Odrzucone zostają w historii. Próba dodania drugiego aktywnego → **409**. `[K]`

> **Anna:** „Jeden aktualny każdego typu. Odrzucone mogą wisieć w historii, ale aktywny tylko jeden. Odrzucony to tak, jakby dokumentu nie było, trzeba donieść nowy.” `[T:99]`

### A7. Statusy i przejścia
Statusy: `PENDING`, `VERIFIED`, `REJECTED`. `[K]` Zmiana przez `PUT …/documents/{documentId}/status` z body `{"status": "…"}`. Niedozwolona zmiana → **409**. `[K]`

> **Anna:** „Ja albo Tomek sprawdzamy i jest albo zweryfikowany, albo odrzucony, na przykład jak na fakturze jest zły VIN.” `[T:85]`

**`REJECTED` jest stanem końcowym.** Poprawka = nowy dokument. To rozstrzyga konflikt Tomek ↔ Anna (sekcja 4, K1).

> **Anna:** „Odrzucony dokument zostaje odrzucony. Jak klient przyniesie poprawkę, wgrywamy nowy dokument. Inaczej przy kontroli nie widać, co było źle.” `[T:111]`
> **Anna:** „Na razie róbcie po mojemu: odrzucony zostaje odrzucony, a poprawka to nowy dokument.” `[T:115]`

Macierz przejść (pozostałe wiersze to założenia, patrz Z3–Z4):

| Z \ Na | PENDING | VERIFIED | REJECTED |
| --- | --- | --- | --- |
| **PENDING** | 409 | ✅ | ✅ |
| **VERIFIED** | 409 | 409 | ✅ (Z3) |
| **REJECTED** | 409 | **409** `[T:111-115]` | 409 |

### A8. Ważność w dniu wydania (włącznie)
Dokument z datą ważności musi być ważny w dniu planowanego wydania; dzień wydania się liczy. Formalnie: `EXPIRED` ⇔ `expiryDate < plannedHandoverDate`.

> **Anna:** „Dokument musi być ważny w dniu wydania auta i ten dzień się jeszcze liczy. Jak polisa kończy się w dniu wydania, to jest w porządku.” `[T:91]`

Data wydania to istniejące pole `Vehicle.plannedHandoverDate` `[KOD]`.

### A9. Gotowość do wydania
`GET /api/vehicles/{vehicleId}/handover-readiness` → `{ ready, missingDocumentTypes, problems[] }`, `reason` ∈ `NOT_VERIFIED`, `EXPIRED`, `NO_HANDOVER_DATE`. `[K]`

Reguły:
1. **Wymagane typy** = 4 stałe + `REGISTRATION_POA`, jeśli `dealerRegistration = true` (A2, A3).
2. **`missingDocumentTypes`** – wymagane typy bez aktywnego dokumentu. Typ, który ma tylko odrzucone dokumenty, jest brakujący. `[T:99]`
3. **`NOT_VERIFIED`** – aktywny dokument wymaganego typu ma status `PENDING`. `[T:85]`
4. **`EXPIRED`** – aktywny dokument wymaganego typu ma `expiryDate < plannedHandoverDate`. `[T:91]`
5. **`NO_HANDOVER_DATE`** (z `documentType: null`) – auto nie ma `plannedHandoverDate`. `[K]`
   > **Anna:** „I jak nie ma ustalonej daty wydania, to nie może pokazywać, że gotowe.” `[T:103]`
6. **`ready`** = brak brakujących typów **i** brak problemów.

> **Tomek:** „Fajnie by było, żebym w aucie od razu widział: gotowe do wydania albo czego brakuje.” `[T:101]`

### A10. UI
- Szczegóły pojazdu: sekcja „Documents” (już jest pusty nagłówek `[KOD: vehicle-detail.html]`) z listą dokumentów, formularzem dodania, akcjami zmiany statusu i blokiem gotowości (gotowe / czego brakuje / problemy). `[T:101]`
- Formularz pojazdu: checkbox „Salon rejestruje auto” (`dealerRegistration`). `[T:81]`

---

## 3. Wymagania funkcjonalne – Feature B: klienci

### B1. Prosta lista klientów, CRUD
`/api/customers` według konwencji modułu pojazdów (GET lista, GET po id, POST 201 + `Location`, PUT, DELETE 204, 404 dla nieistniejącego). `[K]`

> **Anna:** „Chcemy prostą listę klientów: imię, nazwisko, e-mail, telefon, a dla firm NIP.” `[T:109]`

### B2. Pola
`firstName`, `lastName`, `email`, `phone` – wymagane; `nip` – opcjonalny (tylko firmy). `[T:109]`

### B3. Unikalny e-mail
Duplikat e-maila przy tworzeniu i edycji → **409** (jak duplikat VIN w pojazdach `[KOD: VehicleService]`).

> **Anna:** „E-mail nie może się powtarzać.” `[T:109]`

### B4. Bez powiązań z autami
> **Anna:** „Na razie bez powiązań z autami, to później.” `[T:109]`

UI: lista, szczegóły, formularz – jak moduł pojazdów, link w nawigacji.

---

## 4. Sprzeczności w transkrypcji

**K1. Odrzucony dokument: zmiana statusu czy nowy dokument?**
- Tomek `[T:87]`: „jak odrzucimy, a klient doniesie poprawioną wersję, to po prostu przestawiamy status na zweryfikowany”.
- Anna `[T:111-115]`: „Odrzucony dokument zostaje odrzucony […] poprawka to nowy dokument”, „Na razie róbcie po mojemu”.

**Decyzja:** wersja Anny – późniejsza, wprost poprawiająca wcześniejszą wypowiedź, od osoby kierującej administracją sprzedaży, zgodna z A6 („trzeba donieść nowy” `[T:99]`) i z kontraktem („niedozwolona zmiana statusu → 409”). `REJECTED → VERIFIED` zwraca 409. Reguła zamknięta w jednym miejscu (macierz przejść), żeby zmiana po decyzji dyrektora była tania.

---

## 5. Założenia (przyjęte, bo nikt nie odpowie w trakcie)

| # | Założenie | Uzasadnienie |
| --- | --- | --- |
| Z1 | `dealerRegistration` w `VehicleRequest` jest opcjonalne; brak = `false`. | „Domyślnie klient rejestruje sam” `[T:81]`; istniejący klienci API i testy pojazdów nie wysyłają tego pola. |
| Z2 | `PENDING` liczy się jako aktywny dokument (blokuje dodanie drugiego tego typu). | „Aktywny = nieodrzucony” `[K]`, „Odrzucone mogą wisieć w historii, ale aktywny tylko jeden” `[T:99]`. |
| Z3 | `VERIFIED → REJECTED` jest dozwolone. | Bez tego zweryfikowany dokument, w którym później wyjdzie błąd, blokuje auto na zawsze (nie da się dodać nowego – 409). Spójne z „poprawka to nowy dokument” `[T:111]`. |
| Z4 | Przejście na ten sam status i powrót do `PENDING` → 409. | To nie jest „zmiana statusu”; weryfikacja to jednorazowa decyzja `[T:85]`. |
| Z5 | `expiryDate` jest opcjonalna dla każdego typu; dokument bez daty ważności nigdy nie jest `EXPIRED`. | „Nie każdy”, „pełnomocnictwo zwykle też” `[T:91]` – nie ma twardej reguły per typ; kontrakt: „opcjonalnie `expiryDate`” `[K]`. |
| Z6 | `expiryDate` nie może być wcześniejsza niż `issueDate` → 400. | Oczywisty błąd wpisu, analogicznie do `[T:95]`. |
| Z7 | „Dzisiaj” dla walidacji `issueDate` = `LocalDate.now(clock)` serwera (wstrzykiwany `Clock`, testowalny). | Brak informacji o strefie; salon jest w Polsce, aplikacja jednostrefowa. |
| Z8 | Bez daty wydania nie liczę `EXPIRED` (nie ma do czego porównać); zwracam tylko `NO_HANDOVER_DATE` + braki + `NOT_VERIFIED`. | `[T:91]` wiąże ważność z dniem wydania. |
| Z9 | Dokument `PENDING` i jednocześnie przeterminowany → dwa wpisy w `problems` (`NOT_VERIFIED` i `EXPIRED`). | Doradca widzi od razu wszystko, „czego brakuje” `[T:101]`. |
| Z10 | Gdy `dealerRegistration = false`, `REGISTRATION_POA` jest ignorowane w gotowości (nawet jeśli wgrane i np. nieważne). | Nie jest wymagane `[T:81]`, więc nie może blokować wydania. |
| Z11 | Kolejność: `missingDocumentTypes` i `problems` w kolejności typów z kontraktu; `NO_HANDOVER_DATE` na końcu. | Deterministyczna odpowiedź; zgodne z przykładem w `[K]`. |
| Z12 | Ocena wygaśnięcia względem daty wydania, nie względem dzisiaj. | „ważny w dniu wydania” `[T:91]`. |
| Z13 | `number` dokumentu: wymagany, maks. 50 znaków, bez unikalności. | Brak wymagań; konwencja długości z `Vehicle`. |
| Z14 | Dokument innego pojazdu lub nieistniejący pojazd/dokument → 404. | Konwencja `NotFoundException` `[KOD]`. |
| Z15 | Brak usuwania i edycji dokumentów (tylko zmiana statusu). | Kontrakt ich nie przewiduje `[K]`; historia ma być widoczna przy kontroli `[T:111]`. |
| Z16 | Usunięcie pojazdu usuwa jego dokumenty (kaskada w bazie). | Zachowuje działające `DELETE /api/vehicles/{id}`; alternatywa (409) zablokowałaby usuwanie aut z dokumentami. |
| Z17 | E-mail klienta porównywany bez rozróżniania wielkości liter, przycinany; walidacja `@Email`. | „E-mail nie może się powtarzać” `[T:109]` – `Jan@x.pl` i `jan@x.pl` to ten sam adres. |
| Z18 | NIP: opcjonalny, dokładnie 10 cyfr z sumą kontrolną; telefon: 9–15 znaków, cyfry, opcjonalny `+`, spacje. | „dla firm NIP” `[T:109]`; formaty polskie. |
| Z19 | Brak ról i uprawnień – każdy użytkownik może zmieniać status. | „Ja albo Tomek sprawdzamy” `[T:85]`; aplikacja nie ma uwierzytelniania `[KOD]`. |
| Z20 | Brak przesyłania plików skanów – dokument to metadane. | Kontrakt nie ma pola pliku `[K]`; „wgra skan” `[T:85]` traktuję jako opis procesu. |

---

## 6. Pytania do klienta

1. **(K1)** Czy dyrektor potwierdza: odrzucony dokument zostaje odrzucony, poprawka to nowy wpis? (Anna `[T:115]` czeka na decyzję.)
2. **(Z3)** Czy zweryfikowany dokument można później odrzucić (np. błąd wykryty po weryfikacji)?
3. **(Z5)** Czy polisa OC (i pełnomocnictwo) **musi** mieć datę ważności? Dziś pozwalamy dodać bez niej.
4. Czy przy problemach z dokumentem ważne jest też, czy jest ważny **dziś**, czy tylko w dniu wydania?
5. Kto może weryfikować/odrzucać – tylko Anna i Tomek, czy każdy pracownik? Potrzebne logowanie?
6. Czy klient-firma potrzebuje nazwy firmy obok NIP? Czy NIP ma być unikalny?
7. Czy przy kontroli potrzebny jest powód odrzucenia dokumentu (np. „zły VIN”)? `[T:85]`
8. Czy dokumenty mają mieć załącznik (skan), czy wystarczą dane?
9. Czy usuwanie auta z dokumentami powinno być możliwe?

---

## 7. Poza zakresem

| Element | Źródło |
| --- | --- |
| Powiadomienia SMS o gotowości | **Kasia:** „SMS-y to osobna integracja z bramką SMS, więc zostawiamy je na kolejny etap.” `[T:107]` |
| Powiązanie klientów z autami | **Anna:** „Na razie bez powiązań z autami, to później.” `[T:109]` |
| Upload i przechowywanie plików skanów | Brak w kontrakcie `[K]` (Z20) |
| Role, logowanie, audyt kto zmienił status | Brak w transkrypcji i kodzie (Z19) |
| Edycja i usuwanie dokumentów | Brak w kontrakcie (Z15) |
| Wygląd UI ponad poziom modułu pojazdów | Brief: „Nie oceniamy […] wyglądu UI ponad poziom modułu pojazdów.” |

---

## 8. Kryteria akceptacji (skrót, do testów)

- Nowy dokument → `PENDING`; drugi aktywny tego samego typu → 409; po odrzuceniu pierwszego można dodać nowy → 201.
- `issueDate` jutro → 400; dziś → 201. `expiryDate < issueDate` → 400.
- `REJECTED → VERIFIED` → 409; `PENDING → VERIFIED` → 200.
- Polisa z `expiryDate == plannedHandoverDate` → brak `EXPIRED`; dzień wcześniej → `EXPIRED`.
- Auto bez daty wydania z kompletem zweryfikowanych → `ready: false`, problem `NO_HANDOVER_DATE`.
- `dealerRegistration = true` bez pełnomocnictwa → `REGISTRATION_POA` w `missingDocumentTypes`; `false` → nie.
- Tylko odrzucona faktura → `INVOICE` w `missingDocumentTypes`.
- Klient z istniejącym e-mailem (także inną wielkością liter) → 409; bez NIP → 201.
