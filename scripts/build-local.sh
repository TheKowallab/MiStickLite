#!/bin/sh
# Reuses the task-local tools. Does not download/install SDKs or connect to a TV.
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
workspace_dir=$(CDPATH= cd -- "$project_dir/../.." && pwd)
toolchain_dir="$workspace_dir/work/toolchain"
local_jdk=''
for candidate in "$toolchain_dir"/jdk-17*/Contents/Home; do
    if [ -x "$candidate/bin/java" ]; then local_jdk=$candidate; break; fi
done
if [ -z "$local_jdk" ] || [ ! -x "$toolchain_dir/gradle-8.7/bin/gradle" ] || [ ! -d "$toolchain_dir/sdk/platforms/android-35" ]; then
    echo 'Brak lokalnego zestawu w work/toolchain. Ustaw własne JDK/SDK i użyj ./gradlew zgodnie z README.' >&2
    exit 1
fi
export JAVA_HOME="$local_jdk"
export ANDROID_HOME="$toolchain_dir/sdk"
export ANDROID_USER_HOME="$toolchain_dir/.android"
export ANDROID_SDK_HOME="$toolchain_dir"
export GRADLE_USER_HOME="$toolchain_dir/gradle-user"
if [ "$#" -eq 0 ]; then set -- assembleDebug testDebugUnitTest lintDebug; fi
exec "$toolchain_dir/gradle-8.7/bin/gradle" --no-daemon -p "$project_dir" "$@"
