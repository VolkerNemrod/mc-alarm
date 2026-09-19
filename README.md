# 🚨 Alarm System — Minecraft Plugin

> Projekt: system alarmowy, monitoring i czarna skrzynka dla graczy  
> Status: **V1 w implementacji — etapy 0–9 zaimplementowane, trwa testowanie w grze** (stan na 2026-09-19, szczegóły w sekcji 0)  
> Cel: stworzenie modułowego pluginu Paper/Spigot, który pozwala graczom wyposażyć swoje budynki w system alarmowy rejestrujący aktywność innych graczy.

---

## 0. Aktualny stan implementacji

> Stan na **2026-09-19**. Ta sekcja opisuje to, co faktycznie jest w kodzie. Pozostała część dokumentu (sekcje 1–27) to koncepcja i roadmapa.

### Środowisko

- Paper API 1.21.x (w `pom.xml`: `1.21.4-R0.1-SNAPSHOT`; serwer testowy działa na Paper 1.21.11 — wersję API w `pom.xml` warto ujednolicić),
- Java 21, Maven — budowanie: `mvn clean package`,
- SQLite (sqlite-jdbc, maven-shade-plugin),
- nazwa pluginu: `VolkerNemrodAlarm`, pakiet: `pl.volkernemrod.mcalarm`.

### Zaimplementowane

- **Centrala** — blok Lectern z własną recepturą; właściciel, nazwa (zmiana z GUI przez czat), strefa (tworzona automatycznie 7×7×7 przy postawieniu bloku), lista zaufanych, tryby pracy. Jedyny sposób usunięcia: rozbroić i zburzyć fizycznie blok (uzbrojona centrala pulsuje i wybucha zamiast pozwolić się spokojnie rozłożyć) — zawsze kasuje historię i incydenty tej centrali (świadome odstępstwo od GAMEPLAY_SPEC rozdz. 5, decyzja Volkera 2026-09-18: `/alarm remove` usunięte jako zbędne, mem-palace wing mc-alarm).
- **Strefa** — tworzona automatycznie (7×7×7) przy postawieniu bloku centrali; rozmiar regulowany w GUI (pasek wełny w gradiencie kolorów, klasa rozmiaru, obrys cząsteczkami) lub przez `/alarm zone` (alternatywa, ten sam limit 3–15 co GUI).
- **Rejestrowanie zdarzeń** — w strefie rejestrowanych jest 7 typów: `PLAYER_ENTER_ZONE`, `PLAYER_EXIT_ZONE`, `BLOCK_BREAK`, `BLOCK_PLACE`, `CONTAINER_OPEN`, `CONTAINER_ITEM_ADD`, `CONTAINER_ITEM_REMOVE`. Zmiany w kontenerach liczone jako różnica zawartości przed/po (samo przesuwanie przedmiotów nie tworzy wpisów). Ważność: INFO / WARNING / SUSPICIOUS / CRITICAL. Zdarzenia zapisywane są w każdym trybie pracy.
- **Incydenty** — zdarzenia obcego w uzbrojonej strefie grupowane w jedną „wizytę”; zamykane przy wyjściu ze strefy, wylogowaniu, końcu alarmu i ręcznym rozbrojeniu.
- **Alarm** — zdarzenie obcego skonfigurowane jako alarmujące przełącza centralę z CZUWANIE w ALARM: syrena i lampa pulsują co sekundę przez `alarm.domyslny-czas-trwania-sekundy`, właściciel dostaje komunikat, potem centrala wraca do CZUWANIE.
- **Konfiguracja reakcji** — per centrala, które typy zdarzeń wywołują alarm (komenda i GUI).
- **Głośnik** — dodatkowy blok (NOTE_BLOCK z własną recepturą), działa tylko w strefie alarmowej, ma własne GUI (dźwięk ON/OFF, efekty wizualne ON/OFF); podczas alarmu gra dzwonek i wyświetla cząsteczki.
- **GUI centrali** — panel główny, historia (filtr typu, filtr gracza po liście ostatnich, stronicowanie — do 45 zdarzeń na stronę), incydenty (lista i szczegóły incydentu), zaufani (podgląd + dodawanie/usuwanie przez czat, tak jak zmiana nazwy), reakcje, regulacja strefy (pasek wełny w gradiencie, klasa rozmiaru, obrys cząsteczkami, Zapisz/Reset/Wróć), zmiana nazwy, uzbrojenie/rozbrojenie. Dostęp: właściciel i zaufani mogą otworzyć GUI i uzbroić/rozbroić; zarządzanie (strefa, zaufani, reakcje, nazwa) tylko dla właściciela.
- **Odporność na spam i retencja** — cooldowny (wejście do strefy 2 s, bloki 500 ms, otwarcie kontenera 5 s), obsługa teleportacji, automatyczne czyszczenie historii wg `historia.retencja-dni`.

### Receptury

Oba bloki craftuje się w stole rzemieślniczym (układ 3×3, oba układy są symetryczne). Zwykły Lectern i zwykły Note Block **nie** są centralami ani głośnikami — tylko przedmiot z tej receptury ma specjalny znacznik.

**Centrala VolkerNemrodAlarm** (blok Lectern):

```text
[ Sztabka żelaza ] [ Księga ]   [ Sztabka żelaza ]
[ Redstone ]       [   —    ]   [ Redstone ]
[ Sztabka żelaza ] [ Redstone ] [ Sztabka żelaza ]
```

Składniki: 4 × sztabka żelaza, 1 × księga, 3 × redstone. Wynik: 1 × Centrala VolkerNemrodAlarm.

**Głośnik VolkerNemrodAlarm** (blok Note Block):

```text
[ Sztabka żelaza ] [ Redstone ]    [ Sztabka żelaza ]
[ Redstone ]       [ Note Block ]  [ Redstone ]
[ Sztabka żelaza ] [ Redstone ]    [ Sztabka żelaza ]
```

Składniki: 4 × sztabka żelaza, 4 × redstone, 1 × Note Block. Wynik: 1 × Głośnik VolkerNemrodAlarm. Głośnik działa tylko postawiony wewnątrz strefy alarmowej.

Receptury są tymczasowe — docelowo zostaną zmienione wraz z własnymi modelami bloków (resource pack).

### Komendy

```text
/alarm help
/alarm list
/alarm zone pos1 | pos2 | save [nazwa]
/alarm arm [nazwa]
/alarm disarm [nazwa]
/alarm history [nazwa] [liczba] [gracz=<nick>] [typ=<TYP>]
/alarm incidents [nazwa] [liczba]
/alarm trusted add|remove <nick> [nazwa]
/alarm trusted list [nazwa]
/alarm reaction set <typ> <on|off> [nazwa]

/alarm admin list
/alarm admin reload
/alarm admin debug
/alarm admin purge [dni]         (wymaga ponownego wpisania w celu potwierdzenia)
```

### Uprawnienia

| Permisja | Domyślnie |
|---|---|
| `alarm.use`, `alarm.create`, `alarm.manage`, `alarm.history`, `alarm.trusted` | wszyscy |
| `alarm.admin`, `alarm.admin.reload`, `alarm.admin.debug`, `alarm.admin.purge` | OP |

### Konfiguracja (`config.yml`)

- `centrala.zniszczenie-fizyczne.*` — czas pulsowania (3 s), promień eksplozji (3), czy niszczy bloki, czy dotyczy właściciela,
- `alarm.domyslny-czas-trwania-sekundy` — czas trwania alarmu (60 s),
- `historia.retencja-dni` — retencja historii (90 dni, `0` = bez limitu),
- `admin.pokazuj-dane-graczy` — czy `/alarm admin list` pokazuje właścicieli central (domyślnie nie).

### Czeka na weryfikację

- Etap 5 (dawny, plan ROZSZERZENIE GUI): filtry i stronicowanie w GUI historii — zaimplementowane 2026-09-19, jeszcze bez `mvn clean package`/testu w grze,
- Etap 4: dodawanie zaufanych z GUI + D1 — skompilowane, test w grze niepotwierdzony explicit,
- szczegóły incydentu w GUI — build przechodzi (`BUILD SUCCESS`, 2026-09-18), brak testu w grze,
- większość zmian od 2026-09-13 potwierdzona tylko przez `mvn clean package`, bez testu w grze.

### Jeszcze niezaimplementowane (założone w V1)

- rejestrowanie interakcji: drzwi, przyciski, dźwignie (typy `DOOR_USE`, `TRAPDOOR_USE`, `BUTTON_USE`, `LEVER_USE` są zarezerwowane w enumie `TypZdarzenia`, ale nic ich jeszcze nie rejestruje),
- osobny, wybieralny tryb MONITORING (`/alarm mode`) oraz komendy `/alarm info` i `/alarm status`,
- fizyczne, stawiane bloki syreny i lampy (obecnie to efekty przy centrali),
- Etap 10: testy w grze i kryterium ukończenia (sekcja 25),
- własne modele i tekstury bloków (resource pack).

---

## 1. Wizja projektu

Plugin ma działać jak **system alarmowy + monitoring + czarna skrzynka** dla budynków i innych chronionych obszarów.

Główna idea:

> Właściciel budynku instaluje centralę alarmową, definiuje chronioną strefę i decyduje, jakie działania mają być monitorowane oraz które mają powodować alarm.

Plugin **nie ma być przede wszystkim systemem blokującym budowanie/niszczenie**. Jego głównym zadaniem jest odpowiedzieć na pytania:

- Kto wszedł na mój teren?
- Kiedy wszedł?
- Co zrobił?
- Co zniszczył?
- Co postawił?
- Którą skrzynię otworzył?
- Co wyjął ze skrzyni?
- Kiedy opuścił teren?
- Czy zdarzenie powinno wywołać alarm?
- Czy był to właściciel, gracz zaufany czy obca osoba?

W przyszłości plugin może zostać rozszerzony o syreny, światła, karty dostępu, PIN-y, powiadomienia Discord, monitoring, analizę incydentów i wiele innych mechanik.

---

# 2. Założenia pierwszej wersji (V1)

V1 powinna zawierać:

- centralę alarmową,
- system właściciela,
- system zaufanych graczy,
- chronione strefy,
- tryby pracy centrali,
- monitoring aktywności,
- rejestrowanie zdarzeń,
- rejestrowanie zmian w kontenerach,
- klasyfikację zdarzeń,
- historię zdarzeń,
- historię aktywności konkretnego gracza,
- GUI centrali,
- syrenę alarmową,
- lampę alarmową,
- podstawowe uprawnienia,
- trwałe przechowywanie historii.

---

# 3. Główne elementy świata

## 3.1. 🚨 Centrala alarmowa

Najważniejszy blok systemu.

Po postawieniu centrala zostaje przypisana do gracza, który ją zainstalował.

Centrala:

- posiada właściciela,
- posiada własną nazwę/identyfikator,
- jest przypisana do jednej strefy,
- przechowuje konfigurację,
- posiada stan pracy,
- rejestruje zdarzenia,
- posiada listę zaufanych graczy,
- może sterować urządzeniami alarmowymi,
- otwiera panel GUI.

Przykładowe informacje:

```text
Centrala: DOM_VOLKER
Właściciel: Volker
Strefa: DOM
Status: UZBROJONY
Syrena: AKTYWNA
Monitoring: AKTYWNY
```

---

## 3.2. 📡 Czujnik ruchu

Opcjonalne urządzenie rozszerzające zasięg i możliwości systemu.

Czujnik może wykrywać:

- wejście gracza w jego zasięg,
- wyjście gracza,
- obecność gracza,
- ruch w określonym obszarze.

Czujnik może być przypisany do konkretnej centrali.

Przykład:

```text
Centrala
   |
   +-- Czujnik #1 — wejście
   +-- Czujnik #2 — garaż
   +-- Czujnik #3 — magazyn
```

---

## 3.3. 🔊 Syrena alarmowa

Urządzenie odpowiedzialne za fizyczne powiadomienie w świecie Minecraft.

Po aktywacji:

- odtwarza dźwięk,
- może działać przez określony czas,
- może być połączona z centralą,
- może reagować tylko na określone typy zdarzeń.

Przykładowe ustawienia:

- czas alarmu,
- typ dźwięku,
- liczba powtórzeń,
- możliwość automatycznego wyłączenia.

---

## 3.4. 💡 Lampa alarmowa

Wizualny sygnalizator alarmu.

Podczas alarmu może:

- świecić,
- zmieniać stan,
- migać,
- działać razem z syreną.

Może być używana również jako wskaźnik:

- zielony — system aktywny,
- żółty — zdarzenie/podejrzenie,
- czerwony — alarm.

Dokładna implementacja wizualna może zależeć od możliwości wybranej wersji Minecraft/Paper.

---

# 4. System stref

Strefa jest podstawą całego systemu.

Centrala nie powinna ograniczać się wyłącznie do prostego promienia.

Właściciel powinien móc utworzyć obszar:

```text
+-------------------------+
|                         |
|          DOM            |
|                         |
|       🚨 CENTRALA       |
|                         |
|                  📦     |
|                MAGAZYN   |
|                         |
+-------------------------+
```

Strefa może reprezentować:

- dom,
- mieszkanie,
- magazyn,
- farmę,
- garaż,
- warsztat,
- bazę,
- sklep,
- kopalnię,
- dowolny inny obszar.

## V1

Preferowany prosty system definiowania strefy:

- punkt A,
- punkt B,
- prostokątna/kubiczna strefa.

W przyszłości można dodać bardziej zaawansowane kształty.

---

# 5. Wiele central

Jeden gracz może posiadać wiele central.

Przykład:

```text
Volker
│
├── 🏠 DOM
│   └── 🚨 Centrala #1
│
├── 📦 MAGAZYN
│   └── 🚨 Centrala #2
│
└── ⛏️ KOPALNIA
    └── 🚨 Centrala #3
```

Każda centrala może posiadać:

- osobną strefę,
- osobną listę zaufanych,
- osobne ustawienia,
- osobną historię,
- osobny tryb alarmu.

---

# 6. System użytkowników

Każde zdarzenie powinno być przypisane do konkretnego gracza, jeśli jest to możliwe.

## 6.1. Właściciel

Właściciel:

- ma pełny dostęp do centrali,
- może zmieniać ustawienia,
- może zarządzać strefą,
- może dodawać/usuwać zaufanych,
- może przeglądać historię,
- może uzbrajać i rozbrajać system.

Domyślnie działania właściciela nie powinny powodować alarmu.

Opcjonalnie właściciel może zostać uwzględniony w zwykłym logowaniu.

---

## 6.2. Zaufani gracze

Lista graczy, których system zna i którym ufa.

Przykład:

```text
Właściciel:
Volker

Zaufani:
✓ Steve
✓ Alex
✗ Bob
```

Ważne:

**zaufany nie oznacza niewidoczny.**

System może nadal zapisywać:

```text
14:32 Steve wszedł do domu
14:34 Steve otworzył skrzynię
14:35 Steve wyjął 4 DIAMENTY
```

ale nie musi uruchamiać alarmu.

---

## 6.3. Obcy gracz

Gracz nieznajdujący się na liście właściciela/zaufanych.

Może zostać potraktowany jako:

- obserwowany,
- podejrzany,
- intruz,

zależnie od konfiguracji centrali.

---

# 7. Tryby pracy

## 7.1. 🔵 Monitoring

System zapisuje zdarzenia, ale nie uruchamia alarmu.

Przykład:

```text
12:41 Steve wszedł do strefy
12:43 Steve otworzył drzwi
12:44 Steve otworzył skrzynię
12:44 Steve wyjął 4 DIAMENTY
12:45 Steve opuścił strefę
```

---

## 7.2. 🟢 Czuwanie

System aktywnie wykrywa nieautoryzowane działania.

Może generować:

- ostrzeżenia,
- wpisy jako podejrzane,
- powiadomienia,
- potencjalny alarm.

---

## 7.3. 🔴 Alarm

System reaguje na określone zdarzenia.

Możliwe reakcje:

- syrena,
- lampa,
- komunikat,
- powiadomienie właściciela,
- zapis incydentu,
- przyszłościowo Discord/webhook.

---

## 7.4. 🔓 Rozbrojony

System nie generuje alarmów.

Historia może być opcjonalnie nadal zapisywana.

---

## 7.5. 🌙 Tryb nocny

Przyszłościowo centrala może automatycznie uzbrajać się według harmonogramu.

---

## 7.6. 🏠 Właściciel obecny

Przyszłościowo system może zmieniać zachowanie zależnie od obecności właściciela.

Przykład:

```text
Właściciel w strefie → Monitoring
Właściciel poza strefą → Uzbrojenie
```

---

# 8. Rejestrowanie zdarzeń

Plugin powinien posiadać jednolity system zdarzeń.

Każdy wpis powinien w miarę możliwości zawierać:

- czas,
- gracza,
- świat,
- współrzędne,
- strefę,
- centralę,
- rodzaj zdarzenia,
- szczegóły,
- poziom ważności.

Przykład:

```text
[12:44:21]
Gracz: Steve
Strefa: DOM
Akcja: CONTAINER_ITEM_REMOVE
Kontener: Skrzynia #2
Przedmiot: DIAMENT
Ilość: 4
Pozycja: 123,64,-42
```

---

# 9. Kategorie zdarzeń V1

## 🚶 Ruch

- wejście do strefy,
- wyjście ze strefy,
- wejście w zasięg czujnika.

## ⛏️ Bloki

- zniszczenie bloku,
- postawienie bloku,
- interakcja z blokiem,
- użycie drzwi,
- użycie trapdoorów,
- użycie przycisków,
- użycie dźwigni.

## 📦 Kontenery

- otwarcie skrzyni,
- otwarcie beczki,
- otwarcie innych kontenerów,
- zmiana zawartości,
- wyjęcie przedmiotów,
- włożenie przedmiotów.

## 🔥 Inne

- podpalenie,
- użycie wiadra,
- eksplozje,
- niszczenie upraw,
- zabijanie zwierząt — opcjonalnie,
- inne istotne interakcje.

Lista może być rozwijana wraz z testami.

---

# 10. Rejestrowanie przedmiotów

Jedna z najważniejszych funkcji.

Nie wystarczy:

```text
Steve otworzył skrzynię.
```

System powinien — jeśli technicznie możliwe — określić faktyczną zmianę zawartości:

```text
Steve:
- wyjął 4 × DIAMENT
- wyjął 12 × ŻELAZO
+ włożył 32 × COBBLESTONE
```

System powinien rozróżniać:

- dodanie,
- usunięcie,
- zmianę ilości,
- zmianę slotu,
- potencjalne przesunięcie przedmiotu bez faktycznej zmiany zawartości.

Nie należy tworzyć fałszywych wpisów dla zwykłego przesuwania przedmiotów, jeśli stan kontenera się nie zmienił.

---

# 11. Klasyfikacja zdarzeń

Każde zdarzenie może posiadać poziom ważności.

### 🟢 INFO

Przykład:

```text
Steve wszedł do strefy.
```

### 🟡 WARNING

Przykład:

```text
Steve otworzył skrzynię.
```

### 🟠 SUSPICIOUS

Przykład:

```text
Steve wyjął 16 diamentów.
```

### 🔴 CRITICAL

Przykład:

```text
Steve zniszczył blok w trybie alarmowym.
```

Klasyfikacja powinna być konfigurowalna.

---

# 12. Incydenty

Kilka powiązanych zdarzeń może tworzyć jeden incydent.

Przykład:

```text
INCYDENT #124

12:31 Steve wszedł do strefy
12:32 Steve otworzył drzwi
12:33 Steve otworzył skrzynię
12:34 Steve wyjął 4 DIAMENTY
12:37 Steve zniszczył piec
12:40 Steve opuścił strefę
```

Dzięki temu właściciel może zobaczyć nie tylko pojedyncze logi, ale całe zdarzenie.

---

# 13. Historia

## Historia centrali

```text
OSTATNIE ZDARZENIA

12:44 Steve — wyjął 4 DIAMENTY
12:43 Steve — otworzył skrzynię
12:42 Steve — wszedł do strefy
12:31 Alex — wyszedł ze strefy
```

## Historia gracza

Możliwość wyświetlenia:

```text
ŚLEDZTWO: STEVE

Pierwsza obecność: 12:31
Ostatnia obecność: 12:47

ZDARZENIA:
12:31 wejście
12:32 drzwi
12:33 skrzynia
12:34 -4 DIAMENT
12:37 zniszczenie pieca
12:47 wyjście
```

## Historia strefy

Wszystkie istotne zdarzenia dotyczące konkretnej strefy.

---

# 14. GUI centrali

Centrala powinna posiadać czytelne GUI.

Przykład:

```text
┌─────────────────────────────┐
│       🚨 ALARM SYSTEM       │
├─────────────────────────────┤
│ Status: 🟢 UZBROJONY        │
│ Monitoring: AKTYWNY         │
│ Strefa: DOM                 │
│ Właściciel: Volker          │
│                             │
│ Ostatni intruz: Steve       │
│                             │
│ [ HISTORIA ]                │
│ [ INCYDENTY ]               │
│ [ STREFA ]                  │
│ [ ZAUFANI ]                 │
│ [ USTAWIENIA ]              │
│ [ UZBRÓJ / ROZBRÓJ ]       │
└─────────────────────────────┘
```

---

# 15. Konfiguracja reakcji

Właściciel powinien móc wybrać, które zdarzenia powodują alarm.

Przykład:

```text
WEJŚCIE DO STREFY       [ON]
OTWARCIE DRZWI          [ON]
OTWARCIE SKRZYNI        [ON]
WYJĘCIE PRZEDMIOTU      [ON]
ZNISZCZENIE BLOKU       [ON]
POSTAWIENIE BLOKU       [OFF]
PRZYCISK                 [OFF]
DŹWIGNIA                 [ON]
```

To zapobiega sytuacji, w której każda drobna interakcja uruchamia syrenę.

---

# 16. Powiadomienia

## V1

Podstawowe komunikaty w Minecraft:

```text
🚨 ALARM!
Wykryto Steve'a w strefie DOM.
```

## Późniejsze wersje

- Discord webhook,
- prywatna wiadomość,
- powiadomienia administracyjne,
- integracje z zewnętrznymi systemami.

Przykładowe powiadomienie Discord:

```text
🚨 ALARM — DOM

Gracz: Steve
Akcja: Wyjął przedmiot ze skrzyni
Przedmiot: 4 × DIAMENT
Lokalizacja: 123 / 64 / -42
Czas: 22:43
```

---

# 17. Komendy — propozycja

Komendy gracza:

```text
/alarm
/alarm help
/alarm info
/alarm status
/alarm history
/alarm incidents
/alarm zone
/alarm trusted
/alarm trusted add <gracz>
/alarm trusted remove <gracz>
/alarm arm
/alarm disarm
/alarm mode <monitoring|armed|alarm>
```

Komendy administracyjne:

```text
/alarm admin list
/alarm admin info <id>
/alarm admin remove <id>
/alarm admin force-arm <id>
/alarm admin force-disarm <id>
/alarm admin reload
/alarm admin purge <dni>
/alarm admin debug
```

Nazwy mogą zostać zmienione przed implementacją.

---

# 18. Uprawnienia

Proponowane permisje:

```text
alarm.use
alarm.create
alarm.manage
alarm.history
alarm.trusted
alarm.admin
alarm.admin.reload
alarm.admin.debug
alarm.admin.purge
```

System powinien być przygotowany do integracji z LuckPerms.

---

# 19. Przechowywanie danych

V1 powinna posiadać trwałe przechowywanie:

- central,
- właścicieli,
- stref,
- zaufanych graczy,
- konfiguracji,
- urządzeń,
- zdarzeń,
- incydentów.

Dla małych serwerów odpowiednie może być SQLite.

W przyszłości można dodać:

- MySQL/MariaDB,
- PostgreSQL,
- konfigurację wyboru backendu.

---

# 20. Ochrona przed spamem

System nie powinien generować tysięcy wpisów w krótkim czasie.

Należy uwzględnić:

- grupowanie podobnych zdarzeń,
- filtrowanie nieistotnych interakcji,
- ograniczenie częstotliwości zdarzeń ruchu,
- unikanie logowania każdego ticka ruchu gracza,
- deduplikację zdarzeń,
- retencję historii.

Przykład:

Zamiast:

```text
Steve poruszył się
Steve poruszył się
Steve poruszył się
...
```

logujemy:

```text
Steve wszedł do strefy.
```

---

# 21. Retencja danych

Administrator powinien móc ustawić, jak długo przechowywana jest historia.

Przykład:

```text
Historia:
30 dni
90 dni
180 dni
bez limitu
```

Możliwe automatyczne czyszczenie starych danych.

---

# 22. Przyszłe funkcje — ROADMAP

Poniższe funkcje nie muszą być częścią V1, ale architektura projektu powinna pozwalać na ich dodanie.

---

## 22.1. 🔐 System PIN

Centrala może być zabezpieczona PIN-em.

Przykład:

```text
Centrala
[ WPROWADŹ PIN ]

**** 
```

Możliwości:

- zmiana PIN-u,
- blokada po wielu błędnych próbach,
- logowanie błędnych prób,
- osobne PIN-y dla różnych użytkowników.

---

## 22.2. 🪪 Karty dostępu

Specjalny przedmiot:

```text
KARTA DOSTĘPU
Właściciel: Steve
Uprawnienia: DOM
```

Karta może pozwalać na:

- wejście,
- rozbrojenie,
- dostęp do określonych pomieszczeń.

---

## 22.3. Poziomy dostępu

Przykład:

```text
ADMIN
OWNER
TRUSTED
GUEST
MONITORING ONLY
```

Można tworzyć bardziej szczegółowe role:

```text
Steve:
✓ wejście
✓ drzwi
✗ magazyn
✗ rozbrojenie
```

---

## 22.4. 🚪 Inteligentne drzwi

Integracja alarmu z drzwiami.

Możliwe zachowania:

- automatyczne blokowanie,
- otwieranie dla zaufanych,
- zamykanie podczas alarmu,
- logowanie prób wejścia.

---

## 22.5. 🔴 Strefy wielopoziomowe

Jeden budynek może mieć kilka stref:

```text
DOM
│
├── PUBLIC
├── MIESZKALNA
├── MAGAZYN
└── TAJNA
```

Każda może posiadać inne reguły.

---

## 22.6. Czujniki drzwi/okien

Specjalne urządzenia:

```text
🚪 Czujnik drzwi
🪟 Czujnik okna
```

Odpowiedzialne za wykrywanie otwarcia.

---

## 22.7. Czujnik skrzyni

Możliwość monitorowania tylko konkretnego kontenera.

Przykład:

```text
📦 SKARBIEC

Alarm jeśli:
- ktoś otworzy skrzynię,
- ktoś wyjął przedmiot,
- zmieni się zawartość.
```

---

## 22.8. System „cichego alarmu”

Zamiast syreny:

```text
🚨 CICHY ALARM

Intruz wykryty.
```

Właściciel dostaje powiadomienie, ale intruz nie wie, że został wykryty.

---

## 22.9. Fałszywy alarm / alarm opóźniony

Możliwość ustawienia:

```text
Wejście do domu
↓
10 sekund
↓
rozbrojenie przez właściciela
↓
brak alarmu
```

Brak rozbrojenia:

```text
10 sekund
↓
ALARM
```

---

## 22.10. Alarm wielostopniowy

Przykład:

```text
POZIOM 1
Intruz wszedł.

POZIOM 2
Otworzył drzwi.

POZIOM 3
Otworzył skrzynię.

POZIOM 4
Wyjął cenny przedmiot.

POZIOM 5
Zniszczył blok.
```

Każdy poziom może uruchamiać inną reakcję.

---

## 22.11. 💰 System wartości przedmiotów

Możliwość określenia, które przedmioty są „cenne”.

Przykład:

```text
DIAMENT      = bardzo cenny
ZŁOTO        = cenny
ŻELAZO       = normalny
COBBLESTONE  = nieważny
```

Alarm może zostać uruchomiony dopiero po zabraniu przedmiotów o wartości przekraczającej określony próg.

---

## 22.12. 📊 Statystyki

Centrala może pokazywać:

```text
Odwiedziny: 142
Obcy gracze: 17
Incydenty: 8
Alarmy: 3
Najczęstszy gracz: Steve
Najczęstsza akcja: skrzynia
```

---

## 22.13. 🕵️ Tryb śledztwa

Właściciel wybiera gracza:

```text
ŚLEDZTWO: Steve
```

i otrzymuje chronologiczną historię jego działań.

---

## 22.14. 🗺️ Mapa aktywności

Wizualizacja miejsc, w których wystąpiły zdarzenia.

Przykład:

```text
       DOM

   🟢 wejście
        ↓
   🟡 korytarz
        ↓
   🔴 magazyn
        ↓
   🔴 skarbiec
```

---

## 22.15. Ścieżka gracza

Na podstawie zdarzeń można odtworzyć:

```text
WEJŚCIE
  ↓
KORYTARZ
  ↓
MAGAZYN
  ↓
SKRZYNIA
  ↓
WYJŚCIE
```

---

## 22.16. 📹 Wirtualne kamery

Przyszłościowa funkcja.

Kamera może reprezentować punkt obserwacyjny.

GUI może pokazywać:

- nazwę kamery,
- stan,
- ostatnią aktywność,
- ostatniego gracza.

Nie musi oznaczać prawdziwego nagrywania obrazu — może być wizualizacją zdarzeń.

---

## 22.17. Fałszywe kamery

Dekoracyjne kamery mogą odstraszać graczy, ale nie muszą faktycznie monitorować.

---

## 22.18. 👮 Alarm administracyjny

Serwery mogą posiadać centralny system:

```text
ADMIN SECURITY

DOM — ALARM
MAGAZYN — OK
SKLEP — ALARM
BUNKIER — OK
```

Administrator może monitorować wszystkie systemy.

---

## 22.19. 🌐 Panel webowy

W przyszłości:

```text
http://serwer:port
```

Panel:

- lista central,
- historia,
- mapa,
- incydenty,
- statystyki,
- konfiguracja.

---

## 22.20. Discord

Integracja z Discordem:

- alarmy,
- incydenty,
- raporty,
- komendy,
- kanały dla konkretnych central.

---

## 22.21. Raporty okresowe

Przykład:

```text
RAPORT TYGODNIOWY

DOM:
12 odwiedzin
3 obce osoby
2 incydenty

MAGAZYN:
4 odwiedziny
0 incydentów
```

---

## 22.22. System reputacji / zaufania

Opcjonalny system automatycznej oceny:

```text
Steve
Zaufanie: 92%

Alex
Zaufanie: 71%

Bob
Zaufanie: 4%
```

System może uwzględniać historię zachowania.

To powinno być opcjonalne i wyłączone domyślnie.

---

## 22.23. Automatyczne reguły

System:

```text
JEŻELI
gracz = obcy

ORAZ
otwiera skrzynię

ORAZ
przedmiot = DIAMENT

TO
uruchom cichy alarm
```

W przyszłości można stworzyć prosty edytor reguł.

---

## 22.24. Automatyzacja reakcji

Alarm może uruchamiać:

- światła,
- drzwi,
- redstone,
- syreny,
- wiadomości,
- inne urządzenia pluginu.

---

## 22.25. Integracja z redstone

Centrala może działać jako źródło sygnału logicznego.

Przykład:

```text
ALARM
 ↓
REDSTONE
 ↓
drzwi / światła / pułapka / syrena
```

---

## 22.26. Integracja z innymi pluginami

Docelowo warto przygotować API dla:

- WorldGuard,
- LuckPerms,
- ekonomii,
- Discord,
- pluginów logujących,
- pluginów ochronnych,
- systemów map.

---

# 23. Najważniejsza zasada projektowa

Plugin powinien być **modułowy**.

Rdzeń powinien znać:

```text
CENTRALA
STREFA
GRACZ
ZDARZENIE
INCYDENT
ALARM
```

a urządzenia powinny być modułami:

```text
Czujnik
Syrena
Lampa
Drzwi
Kamera
Karta dostępu
```

Dzięki temu można rozwijać plugin bez przebudowy całego systemu.

---

# 24. Priorytety implementacji

## V1 — MUST HAVE

> Stan na 2026-09-19: pozycje z [x] są zaimplementowane i skompilowane (`mvn clean package`). Test w grze wszystkich funkcji jest jeszcze przed nami (Etap 10).

- [x] Centrala
- [x] Właściciel
- [x] Strefa
- [x] Zaufani gracze
- [ ] Monitoring — jako osobny tryb (zdarzenia są zapisywane w każdym trybie, ale nie ma przełącznika MONITORING)
- [x] Uzbrojenie/rozbrojenie
- [x] Rejestrowanie wejścia/wyjścia
- [x] Rejestrowanie niszczenia bloków
- [x] Rejestrowanie stawiania bloków
- [ ] Rejestrowanie interakcji (drzwi, przyciski, dźwignie)
- [x] Rejestrowanie kontenerów
- [x] Rejestrowanie zmian przedmiotów
- [x] Historia
- [x] Historia gracza (`/alarm history gracz=<nick>`)
- [x] Incydenty
- [x] GUI
- [x] Syrena (efekt dźwiękowy przy centrali, plus Głośniki)
- [x] Lampa alarmowa (efekt wizualny przy centrali; fizyczny blok w backlogu)
- [x] Konfiguracja reakcji
- [x] SQLite
- [x] Permisje
- [x] Komendy administracyjne (`list`, `reload`, `debug`, `purge`)

## V1.1

- [ ] Czujniki ruchu
- [ ] lepsza klasyfikacja zdarzeń
- [x] automatyczne grupowanie incydentów
- [x] retencja danych
- [x] lepsze GUI
- [x] bardziej szczegółowe filtry historii (`gracz=`, `typ=`)

## V1.2

- [ ] Discord
- [ ] cichy alarm
- [ ] alarm opóźniony
- [ ] harmonogram uzbrojenia
- [ ] integracja z redstone

## V2+

- [ ] PIN
- [ ] karty dostępu
- [ ] role dostępu
- [ ] wiele poziomów stref
- [ ] kamery
- [ ] mapa aktywności
- [ ] web panel
- [ ] API
- [ ] system reguł
- [ ] automatyzacja
- [ ] raporty
- [ ] statystyki

---

# 25. Definicja sukcesu V1

Pierwszą wersję można uznać za gotową, jeśli gracz może:

1. stworzyć centralę,
2. zostać jej właścicielem,
3. utworzyć strefę,
4. dodać zaufanego gracza,
5. uzbroić system,
6. wejść do strefy innym kontem,
7. zobaczyć wpis o wejściu,
8. otworzyć skrzynię,
9. zobaczyć wpis o otwarciu,
10. wyjąć przedmioty,
11. zobaczyć dokładnie, co zostało zabrane,
12. zniszczyć/postawić blok,
13. zobaczyć te działania w historii,
14. wywołać alarm zgodnie z konfiguracją,
15. usłyszeć syrenę i zobaczyć lampę,
16. przejrzeć historię w GUI,
17. przejrzeć aktywność konkretnego gracza,
18. ponownie uruchomić serwer i zachować całą historię.

---

# 26. Ważna uwaga techniczna dla przyszłej implementacji

Przed rozpoczęciem kodowania należy określić dokładną wersję Minecraft/Paper, ponieważ dostępność i zachowanie części eventów może zależeć od wersji.

Plugin powinien również traktować logowanie jako **best effort**:

> Jeżeli Minecraft/Paper nie udostępnia wiarygodnej informacji pozwalającej przypisać konkretną akcję do gracza, plugin nie powinien udawać, że zna sprawcę.

Szczególnie ważne jest to dla:

- automatycznych mechanizmów,
- hopperów,
- redstone,
- eksplozji,
- zmian kontenerów,
- działań wykonywanych przez inne pluginy.

---

# 27. Docelowa koncepcja

Docelowo plugin powinien przypominać:

```text
                 🚨 ALARM SYSTEM
                       │
                 ┌─────┴─────┐
                 │  CENTRALA │
                 └─────┬─────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
      STREFA         GRACZE        ZDARZENIA
        │              │              │
     ┌──┴──┐       ┌───┴───┐      ┌───┴────┐
     │     │       │       │      │        │
    DOM  MAGAZYN  OWNER TRUSTED  RUCH   BLOKI
                                      │
                                      ├── SKRZYNIE
                                      ├── PRZEDMIOTY
                                      └── INTERAKCJE
                                              │
                                              ▼
                                         INCYDENT
                                              │
                              ┌───────────────┼───────────────┐
                              │               │               │
                            GUI            SYRENA          DISCORD
                              │                               │
                           HISTORIA                         WEB
```

---

## Podsumowanie

Projekt ma być przede wszystkim **systemem obserwacji i alarmowania**, a nie kolejnym pluginem typu WorldGuard.

Najważniejsza funkcja:

> **„Pokaż mi, co wydarzyło się w moim domu, kto to zrobił i kiedy.”**

Dopiero na tym fundamencie budowane są alarmy, syreny, czujniki, karty dostępu, Discord, monitoring, automatyzacja i kolejne funkcje.

README jest mapą funkcjonalną projektu, a nie ścisłą specyfikacją implementacyjną. Przed kodowaniem należy osobno ustalić docelową wersję Minecraft/Paper, format komend, crafting bloków, wygląd GUI oraz szczegółowe zasady logowania poszczególnych typów zdarzeń.
