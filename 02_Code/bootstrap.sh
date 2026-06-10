#!/usr/bin/env bash
# Форк коробки под новый проект: меняет пакет com.eventmap -> твой и имя проекта.
#
# Использование:
#   ./bootstrap.sh <new.package.base> [NewProjectName]
#
# Примеры:
#   ./bootstrap.sh com.acme            # пакеты com.eventmap.* -> com.acme.*
#   ./bootstrap.sh com.acme MyApp      # + rootProject.name = "MyApp"
#
# Что делает:
#   1. заменяет строку пакета com.eventmap на новую во всех .kt/.kts/.plist/манифестах
#   2. переносит физические каталоги .../kz/example -> .../<new>
#   3. (опц.) меняет rootProject.name в settings.gradle.kts
#
# После запуска ОБЯЗАТЕЛЬНО: ./gradlew clean и пересборка. Скрипт — best-effort,
# проверяй сборкой.

set -euo pipefail

OLD_PKG="com.eventmap"
OLD_PATH="kz/example"

NEW_PKG="${1:-}"
NEW_NAME="${2:-}"

if [[ -z "$NEW_PKG" ]]; then
  echo "Укажи новый базовый пакет, напр.: ./bootstrap.sh com.acme [MyApp]" >&2
  exit 1
fi

NEW_PATH="${NEW_PKG//./\/}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

echo ">> Замена пакета '$OLD_PKG' -> '$NEW_PKG' в текстовых файлах…"
# текстовые файлы, кроме сборочных артефактов и .git
grep -rIl --exclude-dir=build --exclude-dir=.git --exclude-dir=.gradle --exclude-dir=.kotlin \
  "$OLD_PKG" . \
  | while read -r f; do
      sed -i.bak "s/${OLD_PKG//./\\.}/${NEW_PKG//./\\.}/g" "$f" && rm -f "$f.bak"
    done

echo ">> Перенос каталогов '$OLD_PATH' -> '$NEW_PATH'…"
# from deepest to shallowest, чтобы вложенные не мешали
find . -type d -path "*/$OLD_PATH" -not -path "*/build/*" -not -path "*/.git/*" -depth \
  | while read -r dir; do
      newdir="${dir/$OLD_PATH/$NEW_PATH}"
      mkdir -p "$(dirname "$newdir")"
      if [[ -d "$newdir" ]]; then
        cp -R "$dir/." "$newdir/" && rm -rf "$dir"
      else
        mv "$dir" "$newdir"
      fi
      echo "   $dir -> $newdir"
    done

if [[ -n "$NEW_NAME" ]]; then
  echo ">> rootProject.name -> '$NEW_NAME'"
  sed -i.bak "s/^rootProject\.name *=.*/rootProject.name = \"$NEW_NAME\"/" settings.gradle.kts && rm -f settings.gradle.kts.bak
fi

echo ""
echo "Готово. Дальше:"
echo "  1) ./gradlew clean"
echo "  2) пересинк в IDE + сборка"
echo "  3) проверь applicationId / namespace в composeApp/build.gradle.kts"
echo "  4) поменяй CoreConfig (baseUrl и т.д.) под свой бэкенд"
