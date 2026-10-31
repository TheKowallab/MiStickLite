#!/bin/sh
# Read-only diagnostics. Pass the exact ADB serial; never picks a device implicitly.
set -eu
if [ "$#" -ne 1 ]; then
    echo "Użycie: $0 SERIAL_ADB" >&2
    exit 2
fi
serial=$1
out="home-snapshot-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$out"
adb -s "$serial" shell getprop ro.product.model > "$out/model.txt"
adb -s "$serial" shell getprop ro.build.version.release > "$out/android.txt"
adb -s "$serial" shell getprop ro.build.version.sdk > "$out/api.txt"
adb -s "$serial" shell getprop ro.build.display.id > "$out/firmware.txt"
adb -s "$serial" shell am get-current-user > "$out/user.txt"
adb -s "$serial" shell cmd package resolve-activity --brief --user current -a android.intent.action.MAIN -c android.intent.category.HOME > "$out/home-before.txt"
adb -s "$serial" shell cmd package query-activities --brief --user current -a android.intent.action.MAIN -c android.intent.category.HOME > "$out/home-candidates.txt"
adb -s "$serial" shell pm list packages -d > "$out/disabled-packages.txt"
adb -s "$serial" shell dumpsys package > "$out/packages.txt"
printf 'Zapisano diagnostykę do %s. Sprawdź ręcznie home-before.txt przed zmianą HOME.\n' "$out"
