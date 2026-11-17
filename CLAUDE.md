# EventMap — инструкции для агента

EventMap — мобильная «живая карта событий» Астаны: Kotlin Multiplatform/Compose клиент,
Ktor-бэкенд и 3D «игровой город» (Three.js в WebView). Владелец и единственный
разработчик — Азамат. Общение и документы — на русском; `02_Code/game-city/README.md`
ведётся на английском — сохраняй язык файла.

## С чего начать

0. **`05_Docs/STATE_FOR_NEXT_AGENT_2026-11-17.md` — короткое актуальное состояние, очередь задач и ловушки.
   Начинать с него.** Пункты ниже — подробности и история.
1. `05_Docs/HANDOFF_FABLE_5.2_2026-11-16.md` — состояние на 16.11.2026 и очередь задач.
   Текущий фокус — **игровой город 3D**.
2. `02_Code/game-city/README.md` — датированные срезы, новейший сверху. Более поздний
   раздел отменяет цифры и описания более раннего.
3. `05_Docs/GAME_CITY_PILOT_2026-11-12.md` — контракт `/world/*`, мост WebView ↔ Kotlin,
   выпуск контента без APK.
4. `05_Docs/ANALYST_HANDOFF_EVENTMAP_2026-11-10.md` — весь продукт: матрица готовности (§13),
   критические пробелы (§14), решения владельца (§18.3).
5. `05_Docs/DOCUMENTATION_INDEX.md` — что канон, что устарело. При противоречии действует
   `EVENTMAP_NEW_CONCEPT.md` с поправками из ANALYST_HANDOFF (игровой город, ролики, 2GIS,
   марафоны возвращены владельцем).

## Карта репозитория

| Путь | Что там |
|---|---|
| `02_Code/game-city/` | 3D-движок (Three.js, esbuild), источники моделей и OSM-данных, импортёры, тесты, превью |
| `02_Code/common/feature/map/ui/.../map/city/` | Kotlin-сторона города: `GameCityView` (expect/actual WebView), `GameCityContent`, `CityCovers`, `VenueBrowser` |
| `02_Code/common/feature/*` | Фичи клиента: `domain` / `data` / `ui` (см. `02_Code/FEATURE.md`) |
| `02_Code/composeApp`, `02_Code/androidApp` | Общий app shell (`MainScreen`) и Android entry point (после миграции AGP 9) |
| `02_Code/androidApp/src/debug` | Debug-only `CityPreviewActivity` / `VenuePreview` — не попадают в release |
| `02_Code/web/` | Публичная веб-версия карты под запуск: оболочка вокруг собранного движка, `events.json`, статическая сборка |
| `02_Code/game-city-unity/` | Изолированный Unity-пилот (6000.3.26f1). Не встроен в мобилку |
| `04_Backend/server/` | Ktor + Exposed + PostgreSQL + Flyway (V1–V16), JWT. `/world/*`, `/venues`, `/admin/venues` |
| `postman/` | Локальные симуляции (Mux, geography, venues) — не production |
| `05_Docs/` | Концепции, API_CONTRACT, ADR, интеграции |
| `01_PM/`, `03_UI_UX/` | Исторические backlog/roadmap/спеки; отчёты в `01_PM/reports` — пустые шаблоны |

## Команды

### Игровой город — `02_Code/game-city` (Node 22)

```sh
npm ci && npm test                                  # 157 тестов на 16.11
node --experimental-vm-modules check-export.mjs     # детерминизм экспорта 19 моделей, ничего не пишет
node build.mjs --preview-only && npm run preview    # только браузерное превью
# http://127.0.0.1:8784/?stage1=1&bubbles=1         # фикстуры, без записи в API
# ...&visualperf=1                                  # харнесс замера кадров
npm run build                                       # движок → composeResources + ресурсы backend
```

- `npm run build` нужен перед мобильной сборкой, если менялся движок или встроенная сцена.
- Геоданные: `node import-addresses.mjs`, `node import-places.mjs --refresh`, `npm test`,
  `node stage-geography.mjs` (Overpass — только ручной импорт, не backend приложения).
- Модели: `npm run export`, `npm test`, `npm run stage-content -- content/manifest.json --release=<метка>`.
- `node_modules` установлен на Mac (esbuild darwin-arm64). В Linux-VM тесты падают с ошибкой
  платформы esbuild — скопируй каталог без `node_modules` во временную папку и сделай `npm ci` там.

### Мобилка — `02_Code`

```sh
./gradlew :androidApp:assembleDebug
./gradlew :common:feature:map:ui:desktopTest :common:feature:event:ui:desktopTest
./gradlew :common:feature:event:data:desktopTest
./gradlew detekt
```

Карта по умолчанию Mapbox (`MAPBOX_DOWNLOADS_TOKEN` как Gradle property и
`MAPBOX_PUBLIC_TOKEN` в `local.properties`, см. `common/feature/map/ui/MAPBOX_SETUP.md`);
`-Peventmap.mapProvider=2gis` собирает без Mapbox (`common/feature/map/ui/2GIS_SETUP.md`). В приложении город — отдельный вход
«3D-город · пилот» во вкладке карты (`MainScreen`, флаг `gameCity`).

### Бэкенд — `04_Backend/server`

```sh
./gradlew build                 # сборка + тесты (H2, без внешних сервисов)
docker compose up --build       # Postgres + сервер на :8080
./gradlew venueAdminPreview     # http://127.0.0.1:4891/admin/venues, ключ local-venue-test-only
```

HTTP-симуляции: `postman simulation run postman/simulations/<geography|venues>/*.sim.yaml ...` —
точные команды в README game-city и `04_Backend/server/README.md`.

## Git

- Рабочая ветка **`codex/eventmap-clean`** → remote `eventmap` (github.com/frtank1/eventMap,
  чистая история). Новые ветки — от неё.
- Локальная `main` и remote `origin` (repo_event_map) — **старая история с Mapbox-токеном**.
  Не мерджить, не пушить, не делать rebase на неё.
- Push, deploy на Render, публикация контента и записи в production — только по явной
  просьбе владельца.
- Коммиты — законченными срезами, сообщения на английском в стиле существующих.

## Секреты

Никогда не коммитить и не печатать значения: `02_Code/local.properties`, `MAPBOX_*`,
`dgissdk.key`, `04_Backend/server/.env`, `JWT_SECRET`, `ADMIN_KEY`, ключи Mux/FCM/S3, `*.jks`.
В документах — только имена переменных. Шаблон — `04_Backend/server/.env.example`.

## Правила проекта

- **Честные статусы.** Различай: есть в коде / частично / целевое требование / предложение.
  Успешная сборка ≠ работающий сценарий; браузерный замер ≠ замер телефона; Android ≠ iOS.
- **Каждый срез документируется:** дата, что сделано, какие проверки прогнаны (с числами),
  что не проверено. Для города — новый раздел сверху в `game-city/README.md`.
- **Никаких sample-подмен в боевых потоках.** Демо и фикстуры — только в явном режиме
  (`?stage1=1`, debug-Activity, `demoMode`).
- **Не выдумывать данные:** адреса, планы этажей, высоты, входы — только из источника
  (снимок OSM, ODbL) или от владельца. Неоднозначность показывать явно. Каталог планов
  `venues/catalog.json` пуст намеренно.
- **Граница WebView:** JS не получает токены; авторизация, публикация, медиа и платежи —
  в Kotlin. JS отдаёт только `ready/place/event/status/content/error`.
- **Бюджеты движка** (менять только с замером): 30 FPS cap, DPR ≤1.35 (auto), ≤2 запроса
  тайлов, ≤9 видимых / 16 в памяти, ≤32 модели / 18 MiB в пакете, анимаций событий ≤4 (2 эконом).
- **Генерируемые файлы руками не править:** `composeResources/files/game-city/*`,
  `game-city/content/assets/*`, `04_Backend/.../resources/world/assets/*`, `game-city/generated/*`.
- **Совместимость пакетов:** схема/движок v2 отвергает v1 и наоборот; опубликованные ID
  этажей/помещений не удалять и не переносить.

## Окружение владельца

MacBook Air (Apple Silicon), Android-эмулятор API 36 (`emulator-5554/5556`). После
блокировки Мака управление окном эмулятора часто пропадает — это не баг приложения.
Render (`eventmap-guo1.onrender.com`, free plan) 12.11 не отвечал на `/health`;
миграции V15/V16 на production не применены.
