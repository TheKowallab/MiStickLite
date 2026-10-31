# MiStick Lite

**Minimalistyczny, lekki launcher dla Android TV.**

MiStick Lite zastępuje rozbudowany pulpit prostym ekranem z zegarem, jednym rzędem aplikacji i menu obsługiwanym pilotem.

Projekt powstał z myślą o urządzeniach z ograniczoną pamięcią RAM, w szczególności Xiaomi Mi TV Stick Full HD.

![Pulpit MiStick Lite](docs/screenshot.png)

## Najważniejsze funkcje

- Jeden poziomy rząd aplikacji, bez rekomendacji i reklam launchera.
- Duży zegar i minimalistyczny, ciemny interfejs.
- Pełna nawigacja pilotem Android TV.
- Zmiana kolejności i ukrywanie aplikacji.
- Automatyczne wykrywanie nowych aplikacji.
- Natywny Kotlin i Android Views, bez Compose.

## Pobieranie i instalacja

Wersja projektu: **1.2.0**.

[Wydania APK — GitHub Releases](https://github.com/TheKowallab/MiStickLite/releases)

Pierwsze publiczne wydanie APK jest w przygotowaniu. Do tego czasu aplikację można skompilować z kodu źródłowego.

Po pobraniu APK można zainstalować aplikację przez ADB. Szczegółowe instrukcje znajdują się dalej w dokumentacji.

## Zgodność i bezpieczeństwo

Projekt testowano na:
- Xiaomi Mi TV Stick Full HD (MiTV-AESP0)
- Android TV 9
- Firmware PI.2055

Działanie na innych urządzeniach i wersjach Android TV nie zostało potwierdzone.

Sama instalacja APK nie zastępuje fabrycznego pulpitu. Zmiany dotyczące systemowego ekranu Home są opcjonalne i wymagają przygotowania procedury przywracania.

Kod projektu jest udostępniany na licencji MIT.

## Ekran i pilot

- Pełny ekran, tło `#121A2B`, kremowy `#F3E8CE`, duży zegar `HH:mm` w lewym górnym rogu i trzy kropki po prawej. Zegar korzysta z czasu i strefy urządzenia.
- Jeden poziomy rząd zaokrąglonych kafelków. Kremowa ramka wskazuje fokus; przewijanie jest natychmiastowe, bez skalowania i animacji.
- **Lewo/prawo:** poprzednia/następna aplikacja. Na końcach fokus zostaje w rzędzie. **OK:** uruchomienie. **Góra:** trzy kropki. **Dół lub lewo z kropek:** powrót do ostatniego kafelka. **Wstecz:** pierwszy kafelek; launcher pozostaje na pulpicie.
- **Przytrzymaj OK na kafelku:** „Zmień kolejność” albo „Ukryj aplikację”. Przy zmianie kolejności lewo/prawo przesuwa kafelek; OK zapisuje, Wstecz anuluje. Wyjście z pulpitu lub zmiana listy pakietów anuluje niezapisane przesuwanie.
- **Przytrzymaj OK na trzech kropkach:** lista ukrytych zainstalowanych aplikacji; wybór przywraca aplikację. Działa również po ukryciu wszystkich kafelków.
- **Krótko OK na trzech kropkach:** dokładnie **Konto, Ustawienia, Sklep z aplikacjami**. Żadnej pozycji „Wszystkie aplikacje”.

Kolejność pierwszego uruchomienia: Netflix, Prime Video, HBO/Max, YouTube, SkyShowtime, TVP VOD, Pilot WP, Apple TV — wyłącznie zainstalowane, dostępne do uruchomienia. Pozostałe aplikacje są dalej w tym samym rzędzie. Pierwszeństwo rozpoznają znane pakiety i nazwy regionalnych wariantów, a nie dołączone atrapy aplikacji. HBO i Max mogą być osobnymi kafelkami, jeżeli oba są zainstalowane.

Nowe pakiety trafiają na koniec, również gdy należą do ośmiu preferowanych usług. Nie ma limitu liczby aplikacji. Usunięte pakiety znikają; ich pozycja i ukrycie pozostają zapamiętane na wypadek reinstalacji. Dane są lokalne w SharedPreferences; `adb install -r` zachowuje je, odinstalowanie aplikacji je usuwa.

## Wykrywanie i koszty działania

PackageManager wyszukuje `ACTION_MAIN + LEANBACK_LAUNCHER`, następnie uzupełnia pakiety przez `ACTION_MAIN + LAUNCHER`. Dla jednego pakietu wybiera jeden punkt wejścia, preferując TV. Pomija własny pakiet, wyłączone/nieeksportowane aktywności i wejścia wymagające uprawnień, których launcher nie ma. Fallback LAUNCHER pozwala uruchamiać również ręcznie zainstalowane aplikacje bez wpisu TV; ich własna obsługa pilota zależy od producenta.

Skan działa poza wątkiem UI przy każdym `onResume` i po `PACKAGE_ADDED`, `PACKAGE_REMOVED`, `PACKAGE_REPLACED`, `PACKAGE_CHANGED`. Krótkie serie zdarzeń są scalane. Powrót na pulpit wykrywa zmiany również wtedy, gdy proces był zabity. Nie ma ciągłego odpytywania, odbiornika boot, reklam, sieci ani żądanych uprawnień aplikacji. Jedyna nowa usługa to opcjonalny HomeKeyDiagnosticService, uruchamiany przez system dopiero po ręcznym włączeniu w ustawieniach dostępności. Nie odczytuje okien ani danych innych aplikacji; nie zmienia działania pulpitu.

Grafiki pochodzą z bannerów zainstalowanych aplikacji, z fallbackiem do ikony i nazwy. Obrazy są próbkowane, maksymalnie 384×216 px dla banneru i 128×128 px dla ikony, wczytywane w pobliżu widocznego obszaru. Wspólny LRU ma limit 3 MiB; kafelki nie trzymają osobnych bitmap. Liczba małych Views i metadanych rośnie z liczbą aplikacji. Jedyną biblioteką wykonawczą jest Kotlin stdlib; brak AndroidX, Compose i bibliotek obrazków w APK. Robolectric i jego komponenty AndroidX są używane wyłącznie na komputerze podczas testów. Limit cache nie jest pomiarem całego RAM procesu — PSS trzeba zmierzyć na Mi Sticku.

## Bezpieczne otwieranie ekranów systemowych

- **Konto:** `Settings.ACTION_SYNC_SETTINGS`, następnie znane wejścia kont Android TV i `ACTION_USER_SETTINGS`; jeśli żadne nie jest dostępne, komunikat i ustawienia systemowe. Nie wymaga odczytu kont, nie dodaje i nie usuwa kont automatycznie.
- **Ustawienia:** `Settings.ACTION_SETTINGS`, `MAIN + LEANBACK_SETTINGS`, a potem dostępne punkty wejścia pakietów ustawień.
- **Sklep:** Google Play `com.android.vending` przez LEANBACK/LAUNCHER, kategorię APP_MARKET lub URI market ograniczone do tego pakietu. Bez Play: komunikat, ustawienia aplikacji, ostatecznie ustawienia systemowe. Nie otwiera przypadkowego sklepu ani strony WWW.

Każda próba sprawdza resolver, dostępność/eksport i obsługuje `ActivityNotFoundException` oraz `SecurityException`. Jeśli wszystko zawiedzie, pokazuje komunikat. Prywatne ekrany firmware mogą pozostać niedostępne — ogólne ustawienia stanowią fallback.

## Budowanie

Wymagane: JDK 17, Gradle 8.7 (wrapper w projekcie), SDK platform 35 i Build Tools 34.0.0. AGP 8.6.1, Kotlin 1.9.25. `compileSdk 35` służy kompilacji; aplikacja nie wymaga Androida 15.

Na komputerze z gotowym SDK/JDK:

```sh
export JAVA_HOME='/ścieżka/do/jdk17/Contents/Home'
export ANDROID_HOME='/ścieżka/do/android-sdk'
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug
```

Można zamiast `ANDROID_HOME` ustawić `sdk.dir=/pełna/ścieżka/do/sdk` w lokalnym, nieprzenoszonym `local.properties`. Wynik: `app/build/outputs/apk/debug/app-debug.apk`. Także wariant debug korzysta z R8 i usuwania nieużywanych zasobów, aby ograniczyć APK. Mapowanie R8 znajduje się w `app/build/outputs/mapping/debug/` po kompilacji. Debug jest podpisany lokalnym kluczem debug; przy aktualizacji trzeba zachować ten sam klucz. Nie jest to wydanie podpisane produkcyjnie.

Na potrzeby tego zadania, po zgodzie użytkownika, narzędzia pobrano do `../../work/toolchain/` względem projektu, bez instalacji systemowej. `scripts/build-local.sh` używa tego lokalnego zestawu i cache. Szczegóły rzeczywistej kompilacji i rozmiar APK znajdują się w `BUILD-REPORT.md`.

Podgląd natywnych Views na modelu API 28 z przykładowymi nazwami usług: [docs/home-sample.png](docs/home-sample.png). Raport: [BUILD-REPORT.md](BUILD-REPORT.md).

## ADB: połączenie i instalacja jako osobna aplikacja

Poniższe polecenia należy wykonać samodzielnie na własnym urządzeniu. MiStick Lite 1.2.0 został fizycznie przetestowany na Xiaomi Mi TV Stick Full HD (MiTV-AESP0), Android TV 9, firmware PI.2055. Działanie na innych urządzeniach nie zostało potwierdzone. Włączenie debugowania i autoryzacja ADB wymagają świadomej decyzji właściciela. Korzystaj wyłącznie z zaufanej sieci lokalnej, a po zakończeniu konfiguracji wyłącz debugowanie, jeśli nie jest już potrzebne.

```sh
adb devices -l
# Jeśli urządzenie ma już włączone debugowanie sieciowe:
adb connect ADRES_IP_STICKA:5555
adb devices -l
export SERIAL='ADRES_IP_STICKA:5555' # albo dokładny serial USB z adb devices
adb -s "$SERIAL" shell getprop ro.product.model
adb -s "$SERIAL" shell getprop ro.build.version.sdk
adb -s "$SERIAL" shell getprop ro.build.display.id
```

Sprawdź model/API/firmware. Najpierw zapisz stan (skrypt tylko odczytuje TV):

```sh
sh scripts/adb-snapshot.sh "$SERIAL"
adb -s "$SERIAL" shell cmd package help
```

W nowym katalogu `home-snapshot-*` sprawdź `user.txt`, `home-before.txt`, `home-candidates.txt` i `disabled-packages.txt`. `home-before.txt` powinien zawierać **rzeczywisty komponent poprzedniego launchera**, np. `pakiet/pełna.nazwa.Aktywności`. Nie zgaduj nazwy na podstawie określenia „Google TV”: to urządzenie może mieć Android TV Launcher/PatchWall. Jeśli resolverem jest `ResolverActivity`, nie ma jednoznacznego domyślnego HOME — zapisz ten fakt i nie używaj tego komponentu jako launchera do rollbacku.

```sh
adb -s "$SERIAL" install -r ../MiStickLite-debug.apk
adb -s "$SERIAL" shell am start -W -n pl.misticklite/.MainActivity
```

To uruchamia launcher jako osobną aplikację, dostępną także w fabrycznym menu TV. Samo zainstalowanie i uruchomienie **nie ustawia go jako domyślnego HOME**. Przetestuj DPAD, trzy pozycje menu, powrót z aplikacji, zmianę kolejności, ukrywanie i przywracanie, przed osobnym włączeniem przekierowania debug przez ADB.


## Ustawienie MiStick Lite jako ekranu Home

Sama instalacja APK nie zastępuje fabrycznego pulpitu. Najpierw uruchom MiStick Lite jako zwykłą aplikację i sprawdź działanie pilota oraz dostęp do ustawień.

### Sprawdzona konfiguracja

MiStick Lite 1.2.0 został przetestowany na:
- Xiaomi Mi TV Stick Full HD (MiTV-AESP0)
- Android TV 9
- Firmware PI.2055

Na tym urządzeniu MiStick Lite działał jako domyślny ekran Home po wyłączeniu fabrycznego pakietu `com.google.android.tvlauncher`.

Podczas testów stwierdzono, że po restarcie system potrafił ponownie włączyć fabryczny launcher. Na testowanym egzemplarzu problem ustąpił po wyłączeniu `com.google.android.tungsten.setupwraith`.

**Uwaga:** wyłączanie SetupWraith może wpływać na konfigurację Android TV, w tym obsługę sieci. To rozwiązanie specyficzne dla przetestowanego urządzenia i firmware. Nie należy stosować go automatycznie na innych telewizorach.

Przed zmianami należy zapewnić działające ADB i przygotować procedurę przywracania systemu. Nie odinstalowuj pakietów systemowych.

### Przywracanie fabrycznego launchera

Jeżeli wcześniej wyłączono poniższe pakiety, można je ponownie włączyć przez ADB:

```sh
adb shell pm enable --user 0 com.google.android.tvlauncher
adb shell pm enable --user 0 com.google.android.tungsten.setupwraith
```

Następnie uruchom ponownie urządzenie i sprawdź działanie fabrycznego ekranu Home.

### Eksperymentalne przekierowanie przycisku Home

Wersja 1.2.0 zawiera również opcjonalny mechanizm diagnostyczny HomeKeyDiagnosticService. Jest on domyślnie wyłączony i nie jest wymagany do działania MiStick Lite jako domyślnego launchera.

Szczegóły:
[HOME-REDIRECT-1.2.md](HOME-REDIRECT-1.2.md)

## Checklista testu na Mi Sticku (do wykonania przez właściciela)

- Zimny start, czytelny zegar i ramka, brak obcięcia na 1920×1080/overscan; sprawdzenie rzeczywistego skalowania Androida.
- Uruchomienie wszystkich dostępnych usług; brak niezainstalowanych preferowanych aplikacji.
- Długi rząd (>8 aplikacji), dotarcie do końca i z powrotem pilotem; góra/dół/krawędzie/Wstecz.
- Zmiana kolejności → OK; druga zmiana → Wstecz. Ukrycie jednej i wszystkich pozycji → przywrócenie z długiego OK na kropkach.
- Ponowne otwarcie, ubicie procesu (`adb shell am force-stop pl.misticklite`) i jawne uruchomienie; kolejność i ukrycie zachowane.
- Instalacja/usunięcie własnej testowej aplikacji TV, aktualizacja pakietu, powrót z Play; nowe pakiety na końcu, brak duplikatów. Nie odinstalowuj płatnych aplikacji ani nie zmieniaj usług systemowych dla testu.
- Każda z trzech pozycji menu; na urządzeniu bez odpowiedniego komponentu komunikat/fallback bez awarii. Nie wyłączaj Play ani ustawień tylko po to, aby testować fallback; taki wariant sprawdzaj na osobnym emulatorze/testowym systemie.
- Pamięć po starcie i po przewinięciu rzędu: `adb -s "$SERIAL" shell dumpsys meminfo pl.misticklite`; zanotuj TOTAL PSS i ewentualne ponowne uruchamianie aplikacji wideo.
- Test fizycznego Home z aplikacji streamingowych, długiego/podwójnego Home i wyłączenia według HOME-REDIRECT-1.2.md; bez zmiany domyślnego HOME. PSS przy UI CLOSED według MEMORY-REPORT.md.

## Testy wersji 1.2.0

```sh
scripts/build-local.sh --offline assembleDebug testDebugUnitTest lintDebug assembleRelease lintRelease
python3 -m unittest discover -s scripts -p test_service_memory.py
```

50 testów Androida/JVM (15 kontrolera gestów, 14 integracyjnych debug i 21 dotychczasowych) oraz 11 testów narzędzia pamięci. Pełny wynik i kontrola niezmienionego renderu: [BUILD-REPORT.md](BUILD-REPORT.md). Brak dodanych zależności wykonawczych.

## Dokumentacja źródłowa

- [Android TV: deklaracja aktywności TV i bannera](https://developer.android.com/training/tv/get-started/create)
- [Settings: intenty systemowe i możliwy brak aktywności](https://developer.android.com/reference/android/provider/Settings)
- [KeyEvent: HOME i ALL_APPS](https://developer.android.com/reference/android/view/KeyEvent)
- [AOSP Android 9 TvSettings — manifest](https://android.googlesource.com/platform/packages/apps/TvSettings/+/android-9.0.0_r1/Settings/AndroidManifest.xml)
- [AGP 8.6: Gradle 8.7, JDK 17 i Build Tools 34](https://developer.android.com/build/releases/agp-8-6-0-release-notes)

Zachowanie konkretnego firmware PI.2055 wymaga fizycznego testu. Źródła AOSP służą do doboru fallbacków; nie potwierdzają, że producent eksportuje te same ekrany.
