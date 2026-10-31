# Diagnostyka Home — MiStick Lite 1.2.0

Użytkownik potwierdził fizyczne HOME(3) DOWN i UP na MiTV-AESP0 / Android 9 / PI.2055 przy użyciu usługi 1.1.0. Wersja 1.2.0 rozszerza ten sam komponent `pl.misticklite/.HomeKeyDiagnosticService` o osobny, debugowy eksperyment krótkiego HOME(3).

**Domyślnie przekierowanie jest OFF**, również po aktualizacji 1.1 → 1.2. Przy OFF usługa tylko diagnozuje 3/284 i przepuszcza klawisze. Tryb ON wymaga jawnej komendy ADB w każdej nowej sesji usługi; nie utrwala się. ALL_APPS(284) nigdy nie jest przekierowywany. Samo zainstalowanie APK nie włącza usługi dostępności.

Pełna procedura instalacji, włączenia, testów długiego/podwójnego Home i awaryjnego wyłączenia: **[HOME-REDIRECT-1.2.md](HOME-REDIRECT-1.2.md)**. Pamięć przy zamkniętej aktywności: [MEMORY-REPORT.md](MEMORY-REPORT.md).

## Dostęp i prywatność

- Komponent jest chroniony przez `android.permission.BIND_ACCESSIBILITY_SERVICE`. Jest widoczny w ustawieniach, ale Android wiąże go dopiero po ręcznym włączeniu.
- `canRequestFilterKeyEvents=true`, `FLAG_REQUEST_FILTER_KEY_EVENTS`, eventTypes=0; `canRetrieveWindowContent=false`. Brak gestów, eksploracji dotykiem, pobierania okien i aktywnej aplikacji. `onAccessibilityEvent` nie odczytuje zdarzenia.
- System może dostarczać inne klawisze, ale kod odrzuca je natychmiast, bez logowania. Poza HOME(3) wszystkie klawisze są przepuszczane także w trybie ON.
- Logcat zawiera dla 3/284 tylko typ/kod, action, repeat, source i flags. Dodatkowe komunikaty techniczne opisują połączenie lub START_REQUESTED / błąd przekierowania. Brak tekstu, nazw aktywnych aplikacji, treści okien, kont czy identyfikatorów sprzętowych w logach.
- W RAM utrzymywany jest tylko stan bieżącej pary Home per urządzenie wejściowe i pojedynczy znacznik czasu do scalania podwójnego naciśnięcia. Nie przechowuje się KeyEvents, listy wcześniejszych naciśnięć ani plików/preferencji trybu. Wewnętrzny numer urządzenia i czasy służą tylko do zbilansowania bieżącego gestu; nie trafiają do logów.
- Brak internetu, telemetrii, roota, timerów i nowych wątków. Tryb i referencje kontrolne nie utrzymują aktywności pulpitu: używane są słabe referencje do własnej MainActivity i usługi.
- Logcat jest krótkotrwałym buforem systemowym, nie bazą historii aplikacji. Do podglądu używaj strumienia bez przekierowania do pliku. CSV ze skryptu pamięci zawierają wyłącznie pomiary RAM i techniczny stan naszej usługi/aktywności, nie logi klawiszy.

## Ręczne ustawienia

Ustawienia Android TV → Preferencje urządzenia / Urządzenie → Ułatwienia dostępu → Usługi / Pobrane usługi → **MiStick Lite — diagnostyka Home**. Nazwy sekcji mogą się różnić na firmware. Wybierz Włącz/Wyłącz i zaakceptuj systemowe potwierdzenie ręcznie. Nie dodano pozycji do menu trzech kropek.

Po ręcznym włączeniu przekierowanie pozostaje OFF. Po wyłączeniu usługi standardowy Home działa według niezmienionych preferencji systemowych. Przed wyłączeniem zwolnij Home; przy zwykłym wyłączaniu przez ADB kod dokańcza skonsumowaną parę. Awaryjne zabicie procesu/komponentu w trakcie gestu nie może zagwarantować dostarczenia brakującego UP.

Wersja 1.2.0 była sprawdzana lokalnie; agent nie instalował jej na Mi Sticku, nie zmieniał ustawień i nie potwierdzał jeszcze fizycznego przekierowania z aplikacji streamingowych.
