#!/bin/sh
# Локальная сборка APK без GitHub Actions.
# Нужны JDK 17 и Android SDK 37 (ANDROID_HOME / ANDROID_SDK_ROOT или local.properties).
set -e
cd "$(dirname "$0")"
chmod +x gradlew
./gradlew assembleRelease
APK="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK" ]; then
  echo "APK не найден: $APK" >&2
  exit 1
fi
cp "$APK" ./AuroraLauncher-2.2.apk
echo "Готово: $(pwd)/AuroraLauncher-2.2.apk"
