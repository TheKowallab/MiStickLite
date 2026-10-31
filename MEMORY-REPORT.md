# Pamięć — MiStick Lite 1.2.0, pulpit zamknięty

**PSS/RAM na Mi Sticku pozostaje niezmierzony.** Agent nie łączył się z urządzeniem ani nie włączał eksperymentu. Przygotowano możliwość porównania usługi przy rzeczywiście zamkniętej aktywności, a nie tylko pulpicie schowanym za inną aplikacją.

## Zmierzone koszty pakietu

| Metryka | 1.1.0 | 1.2.0 debug | Przyrost |
| --- | ---: | ---: | ---: |
| APK, bajty | 146 371 | 167 063 | +20 692 |
| DEX, bajty | 118 012 | 138 496 | +20 484 |
| Klasy DEX | 116 | 136 | +20 |
| Wpisy metod DEX | 795 | 975 | +180 |
| Nowe zależności wykonawcze | — | 0 | 0 |

To rozmiary pakietu, nie RAM. Usługa nadal współdzieli proces `pl.misticklite` z launcherem, bez osobnego `android:process`. Nie ma nowego wątku, timera, pollingu ani trwałej historii klawiszy. Kontroler przechowuje niewielki stan aktualnych gestów Home i pojedynczy czas ostatniej próby uruchomienia. Po UP wpis gestu jest usuwany. Słabe referencje debugowego kontrolera nie utrzymują MainActivity przy życiu.

Włączenie dostępności może utrzymywać cały proces launchera dłużej w RAM. Brak otwartego UI nie oznacza zerowego PSS: pozostają ART, framework, kod, Application i Binder usługi. Nie można wydzielić wiarygodnego PSS pojedynczej klasy ze wspólnego procesu.

## Zamknięcie samego pulpitu

Z katalogu projektu, używając wcześniej autoryzowanego ADB:

```sh
export SERIAL='DOKLADNY_SERIAL_ADB'
sh scripts/home-control.sh "$SERIAL" close-launcher
sh scripts/home-control.sh "$SERIAL" status
```

`close-launcher` kończy **tylko własną MainActivity przez finish()**. Nie robi `force-stop`, nie wyłącza usługi i nie zmienia trybu. Najpierw może zwrócić `launcher=FINISHING`; ponownie odczytaj status po chwili. Do pomiaru wymagaj:

```text
launcher=CLOSED
service=CONNECTED
pendingPairs=0
```

BACKGROUND oznacza nadal istniejącą aktywność w tle i jest odrzucane w scenariuszu zamkniętego UI. CLOSED jest oparte wyłącznie na callbackach cyklu życia własnej aktywności. Nie wymaga odczytu okien ani informacji o innych aplikacjach. Po finish() dotychczasowy kod MainActivity zamyka loader grafik i opróżnia jego cache (limit 3 MiB nie został zmieniony). Zwolnienie pamięci przez GC nie musi być natychmiastowe.

## B: usługa włączona, przekierowanie OFF, UI zamknięte

Usługa musi być już ręcznie włączona w ustawieniach. Zwolnij Home i nie naciskaj go podczas serii.

```sh
sh scripts/home-control.sh "$SERIAL" disable
sh scripts/home-control.sh "$SERIAL" close-launcher
sh scripts/home-control.sh "$SERIAL" status
```

Gdy otrzymasz `mode=OFF service=CONNECTED ... launcher=CLOSED`, odczekaj około 30 sekund dla uspokojenia procesu i zbierz serię:

```sh
python3 scripts/service-memory.py collect "$SERIAL" diagnostic-ui-closed --require-launcher-closed --output memory-samples/B-diagnostic-closed.csv
```

## C: usługa włączona, przekierowanie ON, UI nadal zamknięte

```sh
sh scripts/home-control.sh "$SERIAL" enable
sh scripts/home-control.sh "$SERIAL" status
```

Potwierdź `mode=ON service=CONNECTED ... launcher=CLOSED`. Samo enable nie otwiera pulpitu. Nie naciskaj teraz fizycznego Home — otworzyłby go zgodnie z celem eksperymentu. Po około 30 sekundach:

```sh
python3 scripts/service-memory.py collect "$SERIAL" redirect-ui-closed --require-launcher-closed --output memory-samples/C-redirect-closed.csv
python3 scripts/service-memory.py compare memory-samples/B-diagnostic-closed.csv memory-samples/C-redirect-closed.csv
```

Domyślnie to pięć próbek co trzy sekundy. Skrypt odczytuje debugowe `status` **przed i po każdym dumpsys meminfo** i zapisuje próbkę dopiero po potwierdzeniu CLOSED, CONNECTED, oczekiwanego trybu i braku niedokończonego Home. Zmiana stanu przerywa serię; ostatnia nieprawidłowa próbka nie trafia do CSV. Nie używaj przerwanej częściowej serii jako pełnego wyniku.

To sprawdzenie dwóch momentów, nie atomowy snapshot izolowanej usługi. Unikaj jednoczesnego używania pilota i innych poleceń. Dane zawierają PID i mediany, aby dostrzec restart procesu. Ponowne połączenie usługi resetuje tryb OFF, więc taka seria ON zostanie odrzucona.

## A/D: usługa całkowicie wyłączona

Po zakończeniu pomiaru ON:

```sh
sh scripts/home-control.sh "$SERIAL" disable
sh scripts/home-control.sh "$SERIAL" disable-service
sh scripts/home-control.sh "$SERIAL" status
```

Poczekaj na `mode=OFF service=DISCONNECTED launcher=CLOSED`, następnie zbierz osobną serię **bez** sprawdzania receivera przy każdej próbce:

```sh
python3 scripts/service-memory.py collect "$SERIAL" off-again --output memory-samples/D-service-off.csv
```

Odczyt debugowego receivera może sam uruchomić proces aplikacji, choć nie otwiera UI. Dlatego wariant `--require-launcher-closed` służy porównaniu B/C, gdzie połączona usługa już utrzymuje proces; nie służy do wykazywania, że wyłączony proces nie istnieje. W D samo `dumpsys meminfo` go nie uruchamia. System może zostawić proces jako cache po wyłączeniu usługi; zanotuj to zamiast traktować jako nadal działającą usługę. Nie używaj force-stop podczas porównywalnych serii B/C, bo zabiłby również usługę.

## Wyniki do uzupełnienia na TV

| Stan | Mediana TOTAL PSS (KiB) | PID / uwagi |
| --- | --- | --- |
| B: diagnostyka, UI CLOSED, tryb OFF | niezmierzone | — |
| C: diagnostyka, UI CLOSED, tryb ON | niezmierzone | — |
| D: usługa wyłączona | niezmierzone | — |

C−B przy tym samym PID to orientacyjny dodatkowy koszt trybu ON w wspólnym procesie. Nie jest to całkowity koszt utrzymywania dostępności. Absolutny PSS B/C z zamkniętym UI pokazuje koszt pozostawienia procesu z usługą. D może być niższe, nadal cache'owane albo `not_running`. Brak procesu nie jest zapisywany jako fikcyjne „0 KiB” i nie powoduje wyliczenia niepoprawnej różnicy median. Przy porównaniu różnych PID, stanów UI, GC lub presji pamięci powtórz serię.

CSV przechowują wyłącznie techniczne liczniki i czas pomiaru: nie ma historii klawiszy, nazw okien ani surowego dumpsys/logcat. Skrypt nie włącza/wyłącza trybu, nie uruchamia aktywności i nie zmienia ustawień. Jedynym dodatkowym IPC w wariancie CLOSED jest odczyt statusu debugowego receivera. Wersja release nie ma tego receivera, dlatego te kontrolowane pomiary wymagają dostarczonego debug APK.
