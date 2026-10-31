# MiStick Lite 1.2.0 — eksperymentalny krótki HOME(3)

Na fizycznym MiTV-AESP0 / Android 9 / PI.2055 użytkownik potwierdził w usłudze wersji 1.1.0 HOME(3) DOWN i UP. Wersja 1.2.0 wykorzystuje **HOME(3)**; ALL_APPS(284) pozostaje wyłącznie diagnostyczny i nigdy nie jest konsumowany.

**Po instalacji/aktualizacji, restarcie procesu i każdym ponownym podłączeniu usługi przekierowanie jest OFF.** Tryb jest sesyjny: nie zapisuje się do pliku ani SharedPreferences. Również aktualizacja 1.1 → 1.2 nie może go samoczynnie włączyć. Zamknięcie samego pulpitu nie resetuje trybu, jeżeli usługa nadal działa. Włączenie usługi dostępności i włączenie przekierowania to dwie odrębne czynności.

Nie zmieniono MainActivity, wyglądu, kafelków ani menu: nadal dokładnie Konto, Ustawienia, Sklep z aplikacjami. Kod nie zmienia domyślnego HOME i nie wyłącza `com.google.android.tvlauncher` ani usług Xiaomi. Agent nie wykonywał żadnych z poniższych poleceń na TV.

## 1. Przygotuj istniejące połączenie i drogę powrotu

Użyj już autoryzowanego ADB, właściwego serialu i wersji **debug**. Nie są potrzebne root ani nowe uprawnienia aplikacji.

```sh
adb devices -l
export SERIAL='DOKLADNY_SERIAL_Z_ADB_DEVICES'
adb -s "$SERIAL" shell am get-current-user
adb -s "$SERIAL" shell cmd package resolve-activity --brief --components --user current -a android.intent.action.MAIN -c android.intent.category.HOME
```

Zapisz aktualny ID użytkownika i komponent standardowego, fabrycznego HOME. Nie zgaduj nazwy aktywności z nazwy pakietu. Jeżeli wynikiem jest ResolverActivity albo MiStick Lite zamiast oczekiwanego fabrycznego pulpitu, ustal wcześniej prawidłowy stan wyjściowy. Ten eksperyment nie wybiera nowego domyślnego launchera. Nie wykonuj `set-home-activity` ani nie zmieniaj preferencji HOME na potrzeby tej próby.

Pozostaw drugi terminal z gotowym poleceniem `disable` z sekcji 5. Przed włączaniem/wyłączaniem zwolnij fizyczny przycisk Home.

## 2. Zainstaluj APK 1.2.0, pozostaw przekierowanie OFF

Z katalogu zawierającego pobrany APK:

```sh
adb -s "$SERIAL" install -r MiStickLite-1.2.0-debug.apk
```

Podpis jest ten sam co w dostarczonych 1.0.0 i 1.1.0; `-r` zachowuje kolejność i ukryte aplikacje. Aktualizacja nie włącza usługi dostępności ani przekierowania.

Jeżeli usługa nie działa już po teście 1.1, **ręcznie** otwórz na TV: Ustawienia → Preferencje urządzenia / Urządzenie → Ułatwienia dostępu → Usługi / Pobrane usługi → **MiStick Lite — diagnostyka Home** → Włącz. Nazwy sekcji zależą od firmware. Przeczytaj opis i systemowe potwierdzenie. Sam ten krok pozostawia przekierowanie OFF.

## 3. Potwierdź stan, potem jawnie włącz

```sh
adb -s "$SERIAL" shell am broadcast --user current --receiver-foreground -n pl.misticklite/.HomeRedirectDebugReceiver -a pl.misticklite.debug.HOME_REDIRECT --es command status
```

Oczekiwany fragment wyniku:

```text
Broadcast completed: result=0, data="ok=true mode=OFF service=CONNECTED pendingPairs=0 launcher=... persistent=false"
```

`launcher` może być CLOSED, BACKGROUND albo FOREGROUND; nie jest warunkiem włączenia. Jeśli `service=DISCONNECTED`, sprawdź ręcznie dostępność usługi i poczekaj na połączenie. Polecenie `enable` odrzuca taki stan (`ok=false error=service_not_connected`). Nie włącza usługi samodzielnie.

Gdy widzisz CONNECTED i zwolniony jest Home:

```sh
adb -s "$SERIAL" shell am broadcast --user current --receiver-foreground -n pl.misticklite/.HomeRedirectDebugReceiver -a pl.misticklite.debug.HOME_REDIRECT --es command enable
```

Kontynuuj tylko po `result=0`, `ok=true mode=ON service=CONNECTED pendingPairs=0`. Włączenie nie otwiera aktywności. Dopiero następne kompletne **krótkie** naciśnięcie Home ją otworzy.

Po rozpakowaniu projektu równoważne skróty to:

```sh
sh scripts/home-control.sh "$SERIAL" status
sh scripts/home-control.sh "$SERIAL" enable
sh scripts/home-control.sh "$SERIAL" disable
```

Receiver jest wyłącznie w wariancie debug, wymaga systemowego `android.permission.DUMP` i przyjmuje tylko jawne polecenia. Shell w standardowym Androidzie 9 ma DUMP. Inne uprzywilejowane narzędzia posiadające DUMP także mogą go wywołać; zwykłe obce aplikacje nie mają takiego dostępu. Jeśli firmware odmawia uprawnienia, pozostaw tryb OFF; nie usuwaj ochrony i nie używaj roota.

## 4. Zachowanie i test fizyczny

| Zdarzenie | Tryb OFF | Tryb ON |
| --- | --- | --- |
| HOME(3), krótki DOWN + UP | oba przepuszczane | oba konsumowane; otwarcie po UP, najwyżej raz |
| Powtórzenia HOME / FLAG_LONG_PRESS / długie przytrzymanie | przepuszczane | cała para konsumowana, bez otwarcia MiStick Lite |
| Szybki podwójny HOME | fabryczne zachowanie | pierwsze krótkie naciśnięcie otwiera, drugie jest scalane |
| ALL_APPS(284), Back, DPAD, głośność, inne klawisze | bez zmian | bez zmian |
| HOME, gdy MainActivity jest już wznowiona | fabryczne zachowanie | bez dodatkowego startActivity i bez pętli |

Progi pochodzą z `ViewConfiguration.getLongPressTimeout()` i `getDoubleTapTimeout()` Androida, a nie z timera uruchamianego przez usługę. W testach modelu API 28 użyto 500 ms i 300 ms. Powtórzenie, anulowanie lub flagi długiego naciśnięcia również wykluczają krótkie przekierowanie. Pierwsze krótkie naciśnięcie uruchamia pulpit natychmiast po UP, bez oczekiwania na drugie.

**Ograniczenie długiego/podwójnego Home:** aby zablokować fabryczny krótki Home, usługa musi skonsumować DOWN, zanim wiadomo, czy naciśnięcie będzie długie. Dlatego w trybie ON nie zachowuje oryginalnej fabrycznej akcji długiego i podwójnego Home. Nie odtwarza syntetycznie strumienia klawiszy. Aby odzyskać te funkcje, wyłącz przekierowanie lub usługę. OEM może mieć dodatkową obsługę przycisku poza tą ścieżką; trzeba to sprawdzić fizycznie.

Sprawdź pojedynczo: krótki Home z fabrycznego pulpitu, z Netflix/Prime/innej używanej aplikacji streamingowej, Home na samym MiStick Lite, długie naciśnięcie, szybkie podwójne oraz zwykłe DPAD/Back/głośność. Nie wykonuj serii powtórzeń przed sprawdzeniem dostępnego `disable`.

Opcjonalna obserwacja na żywo, bez zapisywania historii:

```sh
adb -s "$SERIAL" logcat -b main -v raw -T 1 -s MiStickHomeDiag:I '*:S'
```

`redirect=START_REQUESTED` oznacza wywołanie API, nie dowód, że OEM rzeczywiście pokazał aktywność. `redirect=FAILED mode=OFF error=...` oznacza obsłużony błąd; następne naciśnięcie będzie standardowe. Para, dla której wystąpił błąd, pozostaje spójnie skonsumowana. Nie ma automatycznych ponowień ani globalnej akcji HOME.

Uruchamiany jest jawny `pl.misticklite/.MainActivity` przez `ACTION_MAIN + CATEGORY_LAUNCHER`, z flagami `NEW_TASK | SINGLE_TOP | CLEAR_TOP` (`0x34000000`) i istniejącym `launchMode=singleTask`. Nie używa się intentu HOME wybierającego launcher, więc nie powstaje rekurencja resolvera. Nie ma uruchamiania na DOWN, przy łączeniu usługi, po zmianie trybu ani z callbacków aktywności.

Włączenie pomiędzy wcześniej przepuszczonym DOWN a UP nie przechwytuje samego UP. Wyłączenie w trakcie skonsumowanej pary natychmiast anuluje otwarcie, ale konsumuje jeszcze jej kończące UP. `pendingPairs` pokazuje liczbę takich niedokończonych par; po zwolnieniu powinno być 0. Osierocone UP/powtórzenia bez początkowego DOWN są przepuszczane.

Warunkiem bilansowania jest dostarczenie przez system obu końców gestu. Zabicie procesu, utrata połączenia usługi, brak UP w firmware lub awaryjne wyłączenie komponentu w trakcie trzymania klawisza mogą przerwać strumień. Przed zwykłą zmianą ustawień usługi zawsze zwolnij Home. Standardowe `disable-service` odmawia odłączenia, dopóki pozostała skonsumowana para.

## 5. Zwykłe wyłączenie, bez zmiany HOME

```sh
adb -s "$SERIAL" shell am broadcast --user current --receiver-foreground -n pl.misticklite/.HomeRedirectDebugReceiver -a pl.misticklite.debug.HOME_REDIRECT --es command disable
adb -s "$SERIAL" shell am broadcast --user current --receiver-foreground -n pl.misticklite/.HomeRedirectDebugReceiver -a pl.misticklite.debug.HOME_REDIRECT --es command status
```

Oczekuj `mode=OFF pendingPairs=0`. Jeśli pendingPairs=1, zwolnij Home i ponownie sprawdź status. Następny fizyczny krótki Home ma wrócić do standardowego, fabrycznego działania.

Aby wyłączyć także **tylko naszą usługę dostępności**:

```sh
adb -s "$SERIAL" shell am broadcast --user current --receiver-foreground -n pl.misticklite/.HomeRedirectDebugReceiver -a pl.misticklite.debug.HOME_REDIRECT --es command disable-service
```

To najpierw ustawia OFF, potem wywołuje platformowe `disableSelf()` własnej usługi. `disableServiceRequested=true` oznacza wysłaną prośbę; rozłączenie jest asynchroniczne. Po chwili `status` powinien pokazać `mode=OFF service=DISCONNECTED`. Możesz też ręcznie wyłączyć usługę w Ułatwieniach dostępu. Polecenie nie czyści ustawień innych usług.

## 6. Awaryjny powrót przez ADB

Najpierw użyj `disable`/`disable-service` powyżej. Jeśli aplikacja nie odpowiada lub receiver nie działa, w osobnym terminalu z ADB wyłącz **wyłącznie nasz komponent usługi** i zakończ wyłącznie nasz proces. Wstaw rzeczywisty ID użytkownika odczytany wcześniej (zwykle 0):

```sh
export DEVICE_USER='0'
adb -s "$SERIAL" shell pm disable --user "$DEVICE_USER" pl.misticklite/.HomeKeyDiagnosticService
adb -s "$SERIAL" shell am force-stop --user "$DEVICE_USER" pl.misticklite
adb -s "$SERIAL" shell input keyevent 3
adb -s "$SERIAL" shell cmd package resolve-activity --brief --components --user current -a android.intent.action.MAIN -c android.intent.category.HOME
```

Przekierowanie znika: jego stan był wyłącznie w naszym procesie, a usługa nie może się związać, gdy komponent jest wyłączony. Fabryczny HOME nie został zmieniony, więc po usunięciu przechwytywania powinien działać jak przed próbą. Jeżeli chcesz jawnie otworzyć fabryczny pulpit, użyj **zapisanego, sprawdzonego komponentu**:

```sh
export ORIGINAL_HOME='PAKIET/PELNA.NAZWA.AKTYWNOSCI_Z_NOTATKI'
adb -s "$SERIAL" shell am start -W --user "$DEVICE_USER" -n "$ORIGINAL_HOME"
```

To jedynie uruchamia wskazaną aktywność; nie ustawia domyślnego HOME. Nie wyłączaj, nie odinstalowuj ani nie czyść danych `com.google.android.tvlauncher`. Nie wykonuj globalnego `settings put secure accessibility_enabled 0` ani nadpisywania listy usług.

Późniejsze przywrócenie dostępności **naszego** komponentu:

```sh
adb -s "$SERIAL" shell pm enable --user "$DEVICE_USER" pl.misticklite/.HomeKeyDiagnosticService
```

System może pamiętać wcześniejsze włączenie dostępności i ponownie związać usługę, ale jej tryb nadal zacznie od OFF. Sprawdź stan w ustawieniach i przez `status`. Dopiero osobne `enable` z tej instrukcji może ponownie włączyć eksperyment. Nie trzeba odinstalowywać MiStick Lite ani usuwać jego danych.

## 7. PSS bez otwartego pulpitu

Polecenie debugowe `close-launcher` wywołuje `finish()` tylko naszej MainActivity, bez zatrzymania usługi i bez zmiany trybu. Status przechodzi przez FINISHING do CLOSED. BACKGROUND oznacza tylko pulpit w tle i nie jest traktowany jako zamknięcie.

Dokładna procedura, próbki OFF/ON oraz sprawdzanie zamknięcia przed i po każdym odczycie: [MEMORY-REPORT.md](MEMORY-REPORT.md). To PSS wspólnego procesu z działającą usługą i zamkniętą aktywnością; nie jest to osobny, wyizolowany heap usługi.

## Wynik walidacji

Na komputerze: testy obu połówek, zmiany trybu w gestach, długiego/podwójnego Home, błędu startu, braku pętli, komend debug, ochrony receivera i zamykania tylko UI. Lint, podpis i szczegóły: [BUILD-REPORT.md](BUILD-REPORT.md). Wersja 1.2.0 nie była jeszcze instalowana/testowana na TV przez agenta; potwierdzenie fizyczne z 1.1 nie zastępuje tego testu.

Źródła: [AccessibilityService.onKeyEvent/disableSelf](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService), [zadania, singleTask i flagi aktywności](https://developer.android.com/guide/components/activities/tasks-and-back-stack), [manifest Shell Androida 9 — DUMP](https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r1/packages/Shell/AndroidManifest.xml).
