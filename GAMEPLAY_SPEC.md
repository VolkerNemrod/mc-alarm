# GAMEPLAY_SPEC.md

# 🚨 Alarm System — Gameplay Specification

> Szczegółowa specyfikacja mechaniki gry dla pluginu Minecraft Alarm System.  
> Dokument opisuje **jak plugin ma zachowywać się z punktu widzenia gracza**.  
> Nie opisuje implementacji kodu, klas, eventów ani konkretnej technologii.

---

# 1. Cel dokumentu

Ten dokument jest źródłem prawdy dla mechaniki pluginu.

Agent kodujący powinien traktować go jako nadrzędne źródło informacji dotyczących:

- zachowania bloków,
- działania central,
- tworzenia i obsługi stref,
- uprawnień graczy,
- trybów pracy,
- rejestrowania zdarzeń,
- alarmów,
- historii,
- GUI,
- syren i lamp,
- przyszłych rozszerzeń.

Jeżeli implementacja techniczna wymaga decyzji, której nie określa ten dokument, należy wybrać rozwiązanie:

1. przewidywalne dla gracza,
2. bezpieczne dla danych,
3. niewprowadzające fałszywych informacji do logów,
4. łatwe do późniejszego rozszerzenia.

---

# 2. Główna filozofia pluginu

Plugin jest przede wszystkim:

> **systemem monitoringu, alarmowania i czarną skrzynką.**

Nie jest podstawowym pluginem ochronnym.

Plugin nie powinien automatycznie blokować:

- niszczenia bloków,
- stawiania bloków,
- otwierania skrzyń,
- używania drzwi,

jeżeli właściciel nie skonfigurował takiej reakcji.

Podstawowa zasada:

> **Najpierw obserwuj i rejestruj. Dopiero potem reaguj.**

---

# 3. Terminologia

## Centrala

Główny blok systemu alarmowego.

## Właściciel

Gracz, który utworzył/posiada centralę.

## Zaufany

Gracz dodany przez właściciela do listy zaufanych.

## Obcy

Gracz, który nie jest właścicielem ani zaufanym.

## Strefa

Obszar monitorowany przez centralę.

## Zdarzenie

Pojedyncza wykryta akcja.

## Incydent

Logicznie powiązany zestaw zdarzeń.

## Alarm

Aktywna reakcja systemu na zdarzenie spełniające warunki alarmowe.

## Monitoring

Rejestrowanie zdarzeń bez uruchamiania alarmu.

## Uzbrojenie

Aktywna ochrona reagująca na skonfigurowane zdarzenia.

---

# 4. Centrala alarmowa

## 4.1. Postawienie centrali

Gracz stawia blok centrali.

System:

1. rozpoznaje gracza jako właściciela,
2. tworzy nową centralę,
3. nadaje jej unikalny identyfikator,
4. tworzy domyślną konfigurację,
5. pozwala skonfigurować strefę.

Przykład:

```text
🚨 Centrala alarmowa

Właściciel: Volker
ID: ALARM-0001
Strefa: brak
Status: ROZBROJONY
```

---

# 5. Usunięcie centrali

Właściciel może usunąć własną centralę.

Przed usunięciem:

- system powinien wymagać potwierdzenia,
- dane historii nie powinny znikać przypadkowo,
- urządzenia powiązane z centralą powinny zostać odłączone.

Preferowane zachowanie:

> usunięcie centrali nie usuwa automatycznie historii.

Historia powinna zostać zachowana przez określony czas lub do ręcznego usunięcia.

---

# 6. Strefa

## 6.1. Utworzenie

Centrala bez strefy nie posiada aktywnego obszaru monitoringu.

Właściciel uruchamia tryb definiowania strefy.

Następnie wskazuje:

- pierwszy narożnik,
- drugi narożnik.

System tworzy prostokątną/kubiczną strefę.

---

## 6.2. Przykład

```text
PUNKT A
   ↓
+-----------------------+
|                       |
|       CHRONIONA       |
|         STREFA        |
|                       |
|          🚨           |
|                       |
+-----------------------+
                     ↑
                   PUNKT B
```

---

## 6.3. Zasady

Strefa:

- należy do jednej centrali,
- posiada nazwę,
- może być edytowana przez właściciela,
- może zostać usunięta,
- może być aktywowana/dezaktywowana.

---

# 7. Właściciel

Właściciel ma pełny dostęp do swojej centrali.

Może:

- zmienić nazwę,
- zmienić strefę,
- uzbroić system,
- rozbroić system,
- zmienić tryb,
- zarządzać zaufanymi,
- konfigurować reakcje,
- przeglądać historię,
- przeglądać incydenty,
- sterować urządzeniami.

Działania właściciela:

- nie powodują alarmu domyślnie,
- mogą być opcjonalnie logowane jako INFO.

---

# 8. Zaufani gracze

Właściciel może dodać gracza do listy zaufanych.

Przykład:

```text
Zaufani:

✓ Steve
✓ Alex
```

Domyślna zasada:

> Zaufany gracz nie wywołuje alarmu.

Jednocześnie jego działania mogą być nadal rejestrowane.

Przykład:

```text
[INFO]
Steve wszedł do strefy DOM.

[INFO]
Steve otworzył skrzynię.

[INFO]
Steve wyjął 4 × DIAMENT.
```

---

# 9. Obcy gracze

Gracz niebędący właścicielem ani zaufanym jest traktowany jako obcy.

W zależności od ustawień:

- może być tylko logowany,
- może powodować ostrzeżenie,
- może powodować alarm.

---

# 10. Tryby pracy centrali

Centrala posiada jeden aktywny tryb.

## 10.1. ROZBROJONY

System:

- nie generuje alarmów,
- nie uruchamia syren,
- nie uruchamia lamp alarmowych.

Monitoring może być:

- wyłączony,
- włączony — zależnie od konfiguracji.

---

## 10.2. MONITORING

System:

- rejestruje zdarzenia,
- nie uruchamia alarmu,
- może wysyłać informacje właścicielowi.

Przykład:

```text
Steve wszedł do strefy.
Steve otworzył skrzynię.
Steve wyjął 4 DIAMENTY.
```

---

## 10.3. CZUWANIE

System:

- aktywnie obserwuje strefę,
- rozpoznaje obcych,
- klasyfikuje zdarzenia,
- może generować ostrzeżenia,
- może uruchamiać alarm zgodnie z regułami.

---

## 10.4. ALARM

To stan aktywnego alarmu.

Podczas alarmu:

- syrena działa,
- lampa alarmowa działa,
- właściciel otrzymuje komunikat,
- zdarzenie zostaje oznaczone jako incydent,
- kolejne zdarzenia mogą być przypisane do tego samego incydentu.

---

# 11. Uzbrojenie

Właściciel może przełączać:

```text
ROZBROJONY
↓
MONITORING
↓
CZUWANIE
```

Przejście do alarmu następuje automatycznie po spełnieniu warunku.

---

# 12. Alarm

Alarm nie powinien uruchamiać się dla każdego zdarzenia.

Właściciel definiuje reguły.

Przykładowo:

```text
WEJŚCIE OBCEGO       → alarm
OTWARCIE DRZWI       → alarm
OTWARCIE SKRZYNI     → alarm
ZABRANIE DIAMENTÓW   → alarm
POSTAWIENIE BLOKU    → brak alarmu
```

---

# 13. Konfiguracja zdarzeń alarmowych

Każdy typ zdarzenia powinien mieć niezależne ustawienie.

Przykładowa konfiguracja:

```text
Ruch
[✓] wejście do strefy
[ ] wyjście ze strefy

Bloki
[✓] niszczenie
[ ] stawianie
[✓] drzwi
[ ] przyciski
[✓] dźwignie

Kontenery
[✓] otwarcie
[✓] zabranie przedmiotów
[ ] dodanie przedmiotów
```

---

# 14. Rejestrowanie wejścia

Gdy gracz wchodzi do strefy:

```text
EVENT:
PLAYER_ENTER_ZONE
```

Log:

```text
12:41:32
Steve
Wszedł do strefy DOM
```

System nie powinien rejestrować kolejnych wpisów dla tego samego gracza, dopóki nie opuści on strefy i ponownie do niej nie wejdzie.

---

# 15. Rejestrowanie wyjścia

Po opuszczeniu strefy:

```text
PLAYER_EXIT_ZONE
```

Przykład:

```text
12:47:03
Steve
Opuścił strefę DOM
```

---

# 16. Niszczenie bloków

Po zniszczeniu bloku:

```text
BLOCK_BREAK
```

Log powinien zawierać:

- gracza,
- typ bloku,
- pozycję,
- świat,
- czas,
- strefę.

Przykład:

```text
Steve
Zniszczył:
STONE
X: 123
Y: 64
Z: -42
```

Jeżeli zdarzenie jest skonfigurowane jako alarmowe, może uruchomić alarm.

---

# 17. Stawianie bloków

Po postawieniu:

```text
BLOCK_PLACE
```

Przykład:

```text
Steve
Postawił:
CHEST
X: 123
Y: 64
Z: -42
```

Domyślnie:

> postawienie bloku nie powoduje alarmu.

---

# 18. Interakcje z blokami

System może rejestrować m.in.:

- drzwi,
- trapdoory,
- przyciski,
- dźwignie,
- skrzynie,
- beczki,
- piece,
- stoły,
- inne interaktywne bloki.

Nie każda interakcja powinna być logowana jako osobne zdarzenie.

Należy unikać szumu.

---

# 19. Skrzynie i kontenery

## 19.1. Otwarcie

Przy otwarciu kontenera:

```text
CONTAINER_OPEN
```

Przykład:

```text
Steve otworzył:
Skrzynia #2
```

---

# 20. Zmiana zawartości kontenera

Najważniejszy element systemu.

Plugin powinien porównywać stan kontenera przed i po interakcji, jeżeli jest to możliwe i wiarygodne.

Przykład:

Stan przed:

```text
DIAMENT x20
```

Stan po:

```text
DIAMENT x16
```

System zapisuje:

```text
Steve wyjął 4 × DIAMENT
```

---

# 21. Dodawanie przedmiotów

Jeżeli stan zmienił się z:

```text
IRON x10
```

na:

```text
IRON x22
```

system zapisuje:

```text
Steve włożył 12 × IRON
```

---

# 22. Przesuwanie przedmiotów

Plugin nie powinien tworzyć fałszywych wpisów.

Jeżeli gracz tylko przesunął przedmiot między slotami i zawartość kontenera faktycznie się nie zmieniła:

> brak zdarzenia kradzieży.

---

# 23. Hoppery i automatyzacja

Automatyczne przesyłanie przedmiotów nie powinno być przypisywane do gracza bez jednoznacznego dowodu.

Przykład:

```text
Hopper → Chest
```

Nie należy zapisywać:

```text
Steve ukradł przedmiot
```

jeżeli Steve jedynie wcześniej uruchomił mechanizm.

System powinien preferować:

> brak przypisania gracza

zamiast fałszywego przypisania.

---

# 24. Eksplozje

System może rejestrować:

- eksplozję,
- lokalizację,
- zniszczone bloki,
- źródło eksplozji, jeżeli jest znane.

Jeżeli nie da się wiarygodnie ustalić sprawcy:

```text
Sprawca: NIEZNANY
```

Nigdy nie należy zgadywać.

---

# 25. Podpalenie

Możliwe zdarzenie:

```text
FIRE_IGNITE
```

Jeżeli można ustalić gracza:

```text
Steve podpalił blok.
```

W przeciwnym razie:

```text
Nieznane źródło podpalenia.
```

---

# 26. Uprawy

Opcjonalne zdarzenia:

- zniszczenie uprawy,
- zebranie plonów,
- posadzenie.

Powinny być domyślnie wyłączone lub niskiego priorytetu, aby ograniczyć spam.

---

# 27. Zwierzęta

Opcjonalnie:

- zabicie zwierzęcia,
- obrażenie zwierzęcia,
- wejście w interakcję.

Powinno być konfigurowalne.

---

# 28. Poziomy ważności

Każde zdarzenie posiada poziom:

```text
INFO
WARNING
SUSPICIOUS
CRITICAL
```

## INFO

Przykłady:

- wejście,
- wyjście,
- zwykła interakcja.

## WARNING

Przykłady:

- otwarcie skrzyni,
- użycie drzwi.

## SUSPICIOUS

Przykłady:

- zabranie cennych przedmiotów,
- wielokrotne otwieranie kontenerów.

## CRITICAL

Przykłady:

- zniszczenie zabezpieczonego bloku,
- duża kradzież,
- próba włamania.

---

# 29. Wartość przedmiotów

W V1 system może nie posiadać ekonomicznej wyceny przedmiotów.

Jednak mechanika powinna być przygotowana na przyszłą funkcję:

```text
DIAMENT = bardzo cenny
ZŁOTO = cenny
IRON = normalny
COBBLESTONE = nieważny
```

Później możliwe:

```text
Jeżeli wartość skradzionych przedmiotów > 100
→ alarm
```

---

# 30. Incydent

Incydent grupuje zdarzenia, które wystąpiły w krótkim czasie i dotyczą tego samego intruza/strefy.

Przykład:

```text
INCYDENT #124

12:31 Steve wszedł do strefy
12:32 Steve otworzył drzwi
12:33 Steve otworzył skrzynię
12:34 Steve wyjął 4 DIAMENTY
12:37 Steve zniszczył piec
12:47 Steve opuścił strefę
```

---

# 31. Rozpoczęcie incydentu

Incydent może zostać utworzony, gdy:

- obcy wejdzie do uzbrojonej strefy,
- wystąpi skonfigurowane zdarzenie alarmowe,
- właściciel ręcznie rozpocznie śledzenie.

---

# 32. Zakończenie incydentu

Domyślnie incydent kończy się, gdy:

- intruz opuści strefę,
- alarm zostanie zakończony,
- upłynie określony czas bez kolejnych zdarzeń.

---

# 33. Alarm fizyczny

Po uruchomieniu alarmu:

```text
🚨 ALARM
```

System może:

1. uruchomić syrenę,
2. uruchomić lampy,
3. wysłać komunikat,
4. oznaczyć incydent,
5. rozpocząć rejestrowanie kolejnych zdarzeń jako część incydentu.

---

# 34. Syrena

Syrena jest urządzeniem przypisanym do centrali.

Może:

- odtwarzać dźwięk,
- działać przez określony czas,
- być aktywowana ręcznie,
- być aktywowana przez alarm.

Właściciel może mieć możliwość:

```text
[ TEST SYRENY ]
```

---

# 35. Lampa alarmowa

Lampa wizualnie informuje o stanie systemu.

Proponowane stany:

```text
🟢 zielony  = system gotowy
🟡 żółty    = ostrzeżenie
🔴 czerwony = alarm
⚫ brak     = wyłączony
```

Dokładny sposób realizacji zależy od wersji Minecraft.

---

# 36. Automatyczne wyłączenie alarmu

Alarm powinien posiadać limit czasu.

Przykład:

```text
Alarm trwa: 60 sekund
```

Po tym czasie:

- syrena zostaje wyłączona,
- lampa wraca do normalnego stanu,
- incydent pozostaje zapisany.

Możliwe przyszłe ustawienie:

```text
Alarm trwa do ręcznego wyłączenia.
```

---

# 37. Ręczne wyłączenie alarmu

Właściciel może wyłączyć alarm z GUI lub komendą.

Wyłączenie alarmu:

- nie usuwa incydentu,
- nie usuwa historii,
- zapisuje informację o ręcznym zakończeniu.

---

# 38. GUI centrali

Po kliknięciu centrali przez właściciela otwiera się główne GUI.

Przykład:

```text
┌──────────────────────────────┐
│       🚨 ALARM SYSTEM        │
├──────────────────────────────┤
│ Centrala: DOM                │
│ Status: 🟢 UZBROJONA         │
│ Strefa: DOM                  │
│                              │
│ Ostatni gracz: Steve         │
│ Ostatnie zdarzenie:          │
│ Skrzynia → -4 DIAMENT        │
│                              │
│ [ HISTORIA ] [ INCYDENTY ]   │
│ [ STREFA ]   [ ZAUFANI ]     │
│ [ USTAWIENIA ]               │
│ [ UZBRÓJ ]   [ ROZBRÓJ ]     │
└──────────────────────────────┘
```

---

# 39. Historia

Historia musi być filtrowalna.

Filtry docelowe:

- gracz,
- typ zdarzenia,
- poziom ważności,
- czas,
- strefa,
- kontener,
- przedmiot.

Przykład:

```text
HISTORIA → STEVE

[✓] tylko Steve
[ ] wszyscy

Ostatnie 24h
```

---

# 40. Historia gracza

Właściciel wybiera gracza:

```text
ŚLEDZTWO: STEVE
```

System pokazuje:

```text
Pierwsze wejście: 12:31
Ostatnie wyjście: 12:47

Zdarzenia:

12:31 wejście
12:32 drzwi
12:33 skrzynia
12:34 -4 DIAMENT
12:37 piec
12:47 wyjście
```

---

# 41. Historia kontenera

Właściciel może wybrać konkretny kontener.

Przykład:

```text
SKRZYNIA #2

Ostatnie zmiany:

12:34 Steve
-4 DIAMENT

11:20 Volker
+16 DIAMENT

09:15 Alex
-2 ZŁOTO
```

---

# 42. Lista zaufanych

GUI:

```text
ZAUFANI

✓ Steve
✓ Alex
✚ Dodaj gracza
```

Opcje:

- dodaj,
- usuń,
- wyświetl historię,
- opcjonalnie zmień poziom dostępu.

---

# 43. Nazwy central

Właściciel może zmienić nazwę.

Przykłady:

```text
DOM
MAGAZYN
SKLEP
GARAŻ
BUNKIER
```

---

# 44. Powiadomienia w Minecraft

Podstawowe powiadomienia:

```text
🚨 ALARM!
Steve wykryty w strefie DOM.
```

lub:

```text
⚠ Podejrzane zdarzenie!
Steve wyjął 4 × DIAMENT ze skrzyni.
```

---

# 45. Cichy alarm

Przyszła funkcja.

System:

- nie uruchamia syreny,
- nie pokazuje intruzowi informacji,
- wysyła informację właścicielowi,
- zapisuje incydent.

---

# 46. Alarm opóźniony

Przyszła funkcja.

Przykład:

```text
Wejście do strefy
↓
10 sekund
↓
czas na rozbrojenie
↓
brak rozbrojenia
↓
ALARM
```

---

# 47. Tryb nocny

Przyszła funkcja.

Właściciel definiuje harmonogram:

```text
22:00 → UZBROJONY
07:00 → MONITORING
```

---

# 48. Obecność właściciela

Przyszła funkcja.

Przykład:

```text
Volker w domu
→ MONITORING

Volker wychodzi
→ CZUWANIE
```

---

# 49. PIN

Przyszła funkcja.

Centrala może wymagać PIN-u:

```text
Wprowadź PIN:

[ _ _ _ _ ]
```

Błędne próby są logowane.

Możliwa blokada po kilku nieudanych próbach.

---

# 50. Karty dostępu

Przyszła funkcja.

Specjalny przedmiot:

```text
🪪 KARTA DOSTĘPU

Właściciel: Steve
Strefy:
✓ DOM
✗ MAGAZYN
```

---

# 51. Role dostępu

Przyszła funkcja.

Przykładowe role:

```text
OWNER
ADMIN
TRUSTED
GUEST
MONITORING_ONLY
```

Każda rola posiada własne możliwości.

---

# 52. Inteligentne drzwi

Przyszła funkcja.

Możliwe zachowania:

- blokada,
- otwieranie dla zaufanych,
- zamykanie podczas alarmu,
- logowanie prób wejścia.

---

# 53. Strefy wewnętrzne

Przyszła funkcja.

Jedna centrala może mieć podstrefy:

```text
DOM
├── WEJŚCIE
├── MIESZKANIE
├── MAGAZYN
└── SKARBIEC
```

Każda może mieć własne zasady.

---

# 54. Czujniki drzwi i okien

Przyszłe urządzenia:

```text
🚪 Czujnik drzwi
🪟 Czujnik okna
```

Mogą generować:

```text
DOOR_OPEN
DOOR_CLOSE
WINDOW_OPEN
WINDOW_CLOSE
```

---

# 55. Czujnik skrzyni

Przyszłe urządzenie przypisane do konkretnego kontenera.

Może reagować tylko na:

- otwarcie,
- zabranie przedmiotu,
- zmianę zawartości.

---

# 56. Redstone

Przyszła integracja.

Centrala może generować sygnał:

```text
ALARM
 ↓
REDSTONE
 ↓
mechanizm
```

Pozwala to graczom budować własne reakcje.

---

# 57. Automatyczne reguły

Przyszły system:

```text
JEŻELI:
gracz = OBCY

ORAZ:
otworzył skrzynię

ORAZ:
zabrał DIAMENT

TO:
cichy alarm
+ Discord
+ incydent
```

Reguły powinny być czytelne dla gracza.

---

# 58. Discord

Przyszła integracja.

Powiadomienie:

```text
🚨 ALARM — DOM

Gracz: Steve
Akcja: wyjął przedmiot
Przedmiot: 4 × DIAMENT
Pozycja: 123 / 64 / -42
Czas: 22:43
```

---

# 59. Statystyki

Przyszłe statystyki:

```text
ODWIEDZINY: 142
OBCY GRACZE: 17
INCYDENTY: 8
ALARMY: 3

Najczęstszy gracz:
Steve

Najczęstsza akcja:
otwarcie skrzyni
```

---

# 60. Mapa aktywności

Przyszła funkcja wizualna.

Możliwość zobaczenia:

```text
WEJŚCIE
  🟢
   ↓
KORYTARZ
  🟡
   ↓
MAGAZYN
  🔴
   ↓
SKARBIEC
  🔴
```

---

# 61. Wirtualne kamery

Przyszła funkcja.

Kamera jest punktem obserwacyjnym, który może pokazywać ostatnie zdarzenia z danego obszaru.

Nie zakładamy prawdziwego nagrywania obrazu.

---

# 62. Fałszywe kamery

Opcjonalne urządzenia dekoracyjne.

Ich celem może być:

- odstraszanie,
- budowanie klimatu,
- zwiększanie realizmu.

---

# 63. System administracyjny

Administrator serwera może mieć centralny podgląd:

```text
SYSTEM ALARMÓW

DOM       🟢
MAGAZYN   🔴
SKLEP     🟢
BUNKIER   🟡
```

Administrator nie powinien domyślnie otrzymywać dostępu do prywatnych danych graczy, jeśli nie wynika to z konfiguracji serwera.

---

# 64. Retencja historii

Historia powinna mieć konfigurowalny czas przechowywania.

Przykład:

```text
7 dni
30 dni
90 dni
180 dni
bez limitu
```

Automatyczne czyszczenie starych danych.

---

# 65. Ochrona przed spamem

Plugin musi unikać:

- logowania każdego ticka ruchu,
- setek wpisów dla jednej interakcji,
- fałszywych zmian zawartości,
- duplikowania zdarzeń.

Przykład:

Źle:

```text
Steve poruszył się
Steve poruszył się
Steve poruszył się
Steve poruszył się
```

Dobrze:

```text
Steve wszedł do strefy.
```

---

# 66. Zasada wiarygodności logów

Najważniejsza zasada systemu:

> **Jeżeli plugin nie wie, kto wykonał akcję, nie może twierdzić, że wie.**

Przykład:

```text
Zniszczono blok przez eksplozję.

Sprawca: NIEZNANY
```

a nie:

```text
Steve zniszczył blok.
```

jeżeli brak wiarygodnej informacji.

---

# 67. Priorytet poprawności

W przypadku konfliktu:

```text
dokładność > kompletność
```

Lepiej nie zapisać zdarzenia niż zapisać fałszywą informację.

---

# 68. Przyszły system wartości

Możliwe reguły:

```text
Jeżeli:
DIAMENT >= 1
→ WARNING

DIAMENT >= 10
→ SUSPICIOUS

DIAMENT >= 32
→ CRITICAL
```

System powinien pozwalać na konfigurację progów.

---

# 69. Raporty okresowe

Przyszła funkcja:

```text
RAPORT TYGODNIOWY — DOM

Odwiedziny: 32
Obcy: 4
Incydenty: 2
Alarmy: 1

Najczęstszy gość:
Steve
```

---

# 70. API

Przyszła funkcja dla innych pluginów.

Możliwe operacje:

- sprawdzenie strefy,
- zgłoszenie zdarzenia,
- pobranie statusu alarmu,
- aktywacja alarmu,
- pobranie właściciela,
- sprawdzenie poziomu dostępu.

---

# 71. Integracje

Docelowo:

- LuckPerms,
- WorldGuard,
- Discord,
- ekonomia,
- pluginy map,
- pluginy ochronne,
- systemy administracyjne.

Integracje powinny być opcjonalne.

---

# 72. V1 — dokładny zakres

V1 MUSI posiadać:

```text
[✓] Centrala
[✓] Właściciel
[✓] Strefa
[✓] Zaufani
[✓] Rozbrojenie
[✓] Monitoring
[✓] Czuwanie
[✓] Alarm
[✓] Wejście do strefy
[✓] Wyjście ze strefy
[✓] Niszczenie bloków
[✓] Stawianie bloków
[✓] Interakcje
[✓] Otwarcie kontenera
[✓] Zmiany zawartości kontenera
[✓] Dodawanie przedmiotów
[✓] Zabieranie przedmiotów
[✓] Historia
[✓] Historia gracza
[✓] Incydenty
[✓] GUI
[✓] Syrena
[✓] Lampa
[✓] Konfiguracja reakcji
[✓] SQLite
[✓] Permisje
[✓] Komendy
```

---

# 73. V1 — domyślne zachowanie

Po utworzeniu centrali:

```text
Tryb: ROZBROJONY
Monitoring: ON
Alarm: OFF
```

Domyślnie:

```text
Właściciel:
brak alarmu

Zaufany:
brak alarmu

Obcy:
monitorowany
```

---

# 74. V1 — domyślne reakcje

Proponowane wartości:

```text
WEJŚCIE OBCEGO       → WARNING
OTWARCIE DRZWI       → INFO
OTWARCIE SKRZYNI     → WARNING
ZABRANIE PRZEDMIOTU  → SUSPICIOUS
ZNISZCZENIE BLOKU    → CRITICAL
POSTAWIENIE BLOKU    → INFO
```

Właściciel może zmienić te ustawienia.

---

# 75. Przykładowy pełny scenariusz

## Etap 1 — instalacja

Volker stawia centralę.

```text
🚨 Centrala utworzona.

Właściciel: Volker
```

---

## Etap 2 — strefa

Volker definiuje:

```text
DOM
```

---

## Etap 3 — zaufany

Dodaje Steve'a:

```text
Steve → ZAUFANY
```

---

## Etap 4 — uzbrojenie

Volker wybiera:

```text
UZBRÓJ
```

---

## Etap 5 — Steve

Steve wchodzi.

```text
INFO
Steve wszedł do strefy DOM.
```

Brak alarmu.

---

## Etap 6 — Bob

Bob wchodzi.

```text
🚨 OSTRZEŻENIE
Bob wszedł do strefy DOM.
```

Jeżeli wejście jest skonfigurowane jako alarmowe:

```text
🚨 ALARM
```

---

## Etap 7 — Bob otwiera skrzynię

```text
Bob otworzył Skrzynię #2.
```

---

## Etap 8 — Bob kradnie diamenty

Stan:

```text
20 DIAMENTÓW
```

staje się:

```text
16 DIAMENTÓW
```

System:

```text
Bob wyjął 4 × DIAMENT.
```

---

## Etap 9 — Alarm

Jeżeli kradzież jest zdarzeniem alarmowym:

```text
🚨 ALARM!

Bob:
- otworzył skrzynię
- wyjął 4 × DIAMENT
```

Syrena:

```text
WEEE-OOO
```

Lampa:

```text
🔴
```

---

## Etap 10 — Bob niszczy blok

```text
Bob zniszczył:
FURNACE
```

Zdarzenie zostaje dopisane do incydentu.

---

## Etap 11 — Bob wychodzi

```text
Bob opuścił strefę.
```

Incydent zostaje zamknięty.

---

# 76. Rezultat w historii

Volker otwiera:

```text
INCYDENT #124
```

i widzi:

```text
12:31 Bob — wejście
12:32 Bob — drzwi
12:33 Bob — skrzynia #2
12:34 Bob — -4 DIAMENT
12:37 Bob — zniszczył FURNACE
12:47 Bob — wyjście

STATUS:
🔴 WŁAMANIE
```

---

# 77. Zasada rozszerzalności

V1 nie powinna być projektowana wyłącznie pod centralę.

Rdzeń mechaniki powinien zakładać możliwość istnienia:

```text
CENTRALA
├── STREFA
├── UŻYTKOWNICY
├── REGUŁY
├── ZDARZENIA
├── INCYDENTY
└── URZĄDZENIA
    ├── SYRENA
    ├── LAMPA
    ├── CZUJNIK
    ├── DRZWI
    ├── KAMERA
    └── INNE
```

Dzięki temu nowe urządzenia nie wymagają zmiany podstawowej logiki systemu.

---

# 78. Zasada dla agenta kodującego

Agent kodujący NIE powinien samodzielnie dodawać dużych funkcji spoza V1.

Kolejność:

1. zaprojektować V1,
2. zaimplementować V1,
3. przetestować V1,
4. poprawić błędy,
5. dopiero potem rozpocząć V1.1/V2.

Nie implementować od razu:

- PIN,
- kart,
- web panelu,
- kamer,
- Discorda,
- systemu ekonomii,
- zaawansowanych reguł,

jeżeli nie zostało to osobno zlecone.

---

# 79. Kryterium ukończenia mechaniki V1

V1 jest funkcjonalnie gotowa, jeżeli:

- centrala działa,
- właściciel jest poprawnie rozpoznawany,
- można utworzyć strefę,
- można dodać zaufanego,
- można uzbroić i rozbroić system,
- wejście jest wykrywane,
- wyjście jest wykrywane,
- niszczenie jest wykrywane,
- budowanie jest wykrywane,
- interakcje są wykrywane,
- skrzynie są monitorowane,
- faktyczne zmiany zawartości są rejestrowane,
- sprawca jest poprawnie przypisywany tam, gdzie jest to możliwe,
- nie powstają fałszywe wpisy dla zwykłego przesuwania przedmiotów,
- alarm działa zgodnie z konfiguracją,
- syrena działa,
- lampa działa,
- historia jest dostępna,
- incydenty są tworzone i zamykane,
- dane przetrwają restart serwera,
- stare dane mogą być czyszczone,
- administrator posiada podstawowe narzędzia diagnostyczne.

---

# 80. Najważniejsza zasada całego projektu

> ## „System ma pamiętać, co wydarzyło się w chronionej strefie.”

Alarm jest tylko jedną z reakcji.

Najważniejszą wartością pluginu jest **wiarygodna, czytelna i możliwie kompletna historia zdarzeń**.
