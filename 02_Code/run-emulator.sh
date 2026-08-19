#!/usr/bin/env bash
# Запуск Android-эмулятора со стабильным DNS — лечит ERR_NAME_NOT_RESOLVED / UnknownHostException
# на macOS (эмулятор не может достать DNS Mac через свой NAT).
#
# Как пользоваться:
#   1) bash run-emulator.sh           (или ./run-emulator.sh после chmod +x)
#   2) дождись полной загрузки эмулятора
#   3) в Android Studio нажми Run на androidApp — он поставит приложение НА ЭТОТ эмулятор
#      (Studio использует уже запущенный, а не стартует новый со сломанным DNS).
#
# Имя AVD можно передать аргументом: bash run-emulator.sh Pixel_7_API_34
set -e

SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
AVD="${1:-Medium_Phone_API_36.0}"

echo "→ Глушу запущенные эмуляторы..."
"$SDK/platform-tools/adb" emu kill 2>/dev/null || true
sleep 2

echo "→ Запускаю $AVD с DNS 8.8.8.8 (холодная загрузка)..."
echo "  (окно эмулятора откроется; не закрывай терминал — он держит эмулятор)"
exec "$SDK/emulator/emulator" -avd "$AVD" -no-snapshot-load -dns-server 8.8.8.8
