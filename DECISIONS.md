# DECISIONS

Dziennik decyzji, pomyłek AI i ręcznych poprawek. Wymagania i założenia (Z1–Z20): `INTENT.md`.
Narzędzie: Claude Code (Opus 5.5), praca faza po fazie wg `docs/EXECUTION_PLAN.md`, każdy plan akceptowany przez człowieka.

## 1. Proces i kontekst dla agenta

| Krok | Co zrobiono | Dlaczego |
| --- | --- | --- |
| Analiza | `INTENT.md` przygotowany w osobnej sesji, zaakceptowany przed implementacją | Kontrakt API nadrzędny; sprzeczność Tomek ↔ Anna (K1) rozstrzygnięta jawnie, zanim powstał kod |
| `CLAUDE.md` | 72 linie: komendy przez kontener, konwencje z modułu pojazdów w formie sprawdzalnych reguł, twarde zakazy, definicja „gotowe” | Krótki kontekst ładowany zawsze; szczegóły generowania przeniesione do skilla, żeby nie dublować |
| Skill `feature-slice` | `SKILL.md` (procedura + checklista) + `reference/` (mapa typów, szablony backend/frontend na encji-placeholderze `Widget`, macierz obowiązkowych testów, pułapki) | Agent bez skilla zgaduje strukturę; skill daje kod wyprowadzony 1:1 z modułu pojazdów oraz rzeczy, których z kodu nie widać (normalizacja unikalnych pól, `saveAndFlush` w teście constraintu, `Clock`, wariant zasobu zagnieżdżonego, „nie generuj endpointów spoza kontraktu”) |
| Logi | Hook eksportuje też pełne surowe transkrypty do `ai-logs/raw/`, maskując e-mail | Brief wymaga pełnych logów; adres użytkownika nie może trafić do publicznego repo |

## 2. Pomyłki AI i jak je wyłapano

| # | Pomyłka | Jak wykryto | Poprawka |
| --- | --- | --- | --- |
| P1 | Redakcja logów sprawdzana loginem z `git config user.email`, który jest innym adresem niż konto Claude – adres konta wyciekł do dwóch lokalnych commitów z logami | Kontrolny `grep` po właściwym loginie w `ai-logs/` i `git log -p` | Hook czyta dodatkowe adresy z nieśledzonego `.claude/redact.local`; logi zregenerowane, oba niewypchnięte commity odtworzone (historia czysta, `git log -p --all` = 0 trafień) |
| P2 | Pierwsza wersja skilla używała w przykładach `Customer`, „documents of a vehicle” i walidatora NIP | Bramka fazy: `grep` skilla po nazwach z zadania | Przykłady zamienione na neutralne (`Supplier`, `inspections`, `Ean13`) – inaczej test skilla na klientach mierzyłby podpowiedzi, a nie skill |

## 3. Feature B – klienci (wyłącznie skillem)

Metoda: skill uruchamiany w **świeżym subagencie** (bez kontekstu tej rozmowy), z samym opisem encji z `INTENT.md` B1–B3, Z17, Z18 – tak jak uruchomią go oceniający.

### Iteracja 1 (skill v1) – wynik zielony, ale z obejściami
Subagent wygenerował komplet (backend 52 testy, frontend 14), `test.sh` zielony, smoke test API OK. Przegląd jego raportu i kodu pokazał, że **sam musiał obejść błędy skilla**:

| # | Problem skilla v1 | Skutek | Poprawka w skillu (commit `cdf1f83`) |
| --- | --- | --- | --- |
| S1 | Przepis na unikalność bez wielkości liter: normalizacja (`trim` + lowercase) w mapperze. Dla e-maila `@Email` odrzuca `" Jan@x.pl "` **przed** mapperem | Czerwony test; agent dopisał compact constructor w `CustomerRequest` poza szablonem | Szablon compact constructora (trim) + pułapka w `pitfalls.md` |
| S2 | Regex telefonu `\+?[0-9 ]{9,15}` liczył spacje jako cyfry (`"1 2 3 4 5"` przechodził), `VARCHAR(20)` niespójny | Agent napisał własny regex | `\+?[0-9]( ?[0-9]){8,14}`, `VARCHAR(30)`, zasada „długość kolumny = najdłuższa akceptowana wartość” |
| S3 | Brak wskazówki dla testu za długiego e-maila (`"a".repeat(89)` łamie też limit 64 znaków części lokalnej) | 2 błędy zamiast 1 | Pułapka w `backend-tests.md` |
| S4 | Szablon spec listy bez przypadku nieudanego usuwania | Agent dopisał sam | Dodany test `deleteError` |
| S5 | Niejasna kolejność kolumn listy dla osób (klucz vs nazwisko) | Agent przestawił e-mail na pierwszą kolumnę | Reguła: naturalna kolejność pól, link na kolumnie klucza |
| S6 | Brak konwencji komunikatu własnej walidacji i informacji o `@ParameterizedTest` | Drobne niespójności | Doprecyzowane |

Decyzja: **nie poprawiam kodu ręcznie** – v1 odłożona (`git stash`), skill poprawiony, klienci generowani ponownie.

### Iteracja 2 (skill v2) – przyjęta, commit `8c8f3dd`
Nowy świeży subagent, ten sam opis. Wynik: backend 45 testów klientów (serwis 11, kontroler 18, repozytorium 3, walidator NIP 13), frontend 14; `test.sh` zielony (sprawdzone przeze mnie ponownie), smoke test API i ręczny test w UI (409 dla `JAN.Kowalski@example.com` przy istniejącym `jan.kowalski@…`, 400 telefonu pod polem).
Obejście trim e-maila i własny regex telefonu **zniknęły** – kod jest wprost z szablonów.

Jedyne odstępstwo od szablonu: wartość testowa za długiego e-maila. Przyczyna – **moja** rada w skillu v2 była błędna (jedna etykieta domeny 95 znaków, a `@Email` dopuszcza max 63). To pomyłka AI przy poprawianiu skilla, wykryta przez czerwony test subagenta.

| # | Uwaga z iteracji 2 | Poprawka w skillu v3 (`18b7a10`) |
| --- | --- | --- |
| S7 | Błędna rada „wydłuż domenę” | Kilka etykiet domeny, opis limitów 64/63 |
| S8 | Off-by-one przy teście telefonu (15 cyfr jest poprawne → mock zwraca `null` → NPE) | Wartości graniczne 8/9/15/16 w mapie typów i pułapkach testów |
| S9 | Brak szkieletu testu walidatora, brak wierszy macierzy dla `{}` i trim przez HTTP | Szkielet `@ParameterizedTest`, nowe wiersze macierzy |
| S10 | Konwencja komunikatu 409, helpery spec (`fill` po typie, zbędny `select()`) | Doprecyzowane |

Trzeciej generacji nie robiłem: kod v2 nie wymagał zmian, a poprawki v3 dotyczą testów i opisu. **Ręcznych poprawek kodu klientów: brak.**

## 4. Feature A – dokumenty i gotowość

_(uzupełniane w trakcie fazy 4)_

## 5. Decyzje projektowe

_(uzupełniane w trakcie)_
