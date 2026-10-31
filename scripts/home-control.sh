#!/bin/sh
# Explicit debug-only ADB command. Never connects to a TV or enables accessibility.
set -eu
if [ "$#" -ne 2 ]; then
    echo "Użycie: $0 SERIAL_ADB status|enable|disable|disable-service|close-launcher" >&2
    exit 2
fi
serial=$1
command=$2
case "$command" in
    status|enable|disable|disable-service|close-launcher) ;;
    *) echo 'Nieznane polecenie.' >&2; exit 2 ;;
esac
exec adb -s "$serial" shell am broadcast --user current --receiver-foreground \
    -n pl.misticklite/.HomeRedirectDebugReceiver \
    -a pl.misticklite.debug.HOME_REDIRECT --es command "$command"
