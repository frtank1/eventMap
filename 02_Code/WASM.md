# Включение wasm (web) — план

Сейчас web не собирается: таргета `wasmJs` нет ни в одном модуле, а часть зависимостей
в текущих версиях вообще не имеет wasm-артефактов. Просто добавить таргет нельзя —
Gradle-синк упадёт на резолве. Нужен апгрейд версий, и каждый шаг проверять сборкой.

## Блокеры по зависимостям

| Либа | Сейчас | Нужно для wasm |
|---|---|---|
| koin-core | 3.5.3 | 4.0+ (wasmJs появился в Koin 4.0) |
| multiplatform-settings | 1.0.0 | 1.1.0+ |
| ksafe | 1.7.1 | проверить wasm; скорее всего нет — нужна абстракция хранилища |
| ktor | 3.4.0 | ок, но добавить движок `ktor-client-js` для wasmJs |
| filekit-core | 0.8.0 | проверить wasm; на web файлы работают иначе |
| voyager | 1.1.0-beta02 | wasm вроде есть, проверить |
| compose / material3 / lifecycle | — | wasm поддерживают |

## Порядок (каждый шаг = синк/сборка, не идти дальше при ошибке)

1. Поднять koin до 4.x. Проверить API (startKoin/module/single не менялись, но ViewModel-API в 4.0 другой). Собрать android — убедиться, что ничего не отвалилось.
2. Поднять multiplatform-settings до 1.1.0+.
3. Хранилище: KSafe не умеет wasm → завернуть хранилище в `expect/actual` в `core/datastore`,
   на wasm дать реализацию на multiplatform-settings (StorageSettings/localStorage),
   KSafe оставить только для android/ios/desktop.
4. ktor: добавить `ktor-client-js` в `wasmJsMain` сети, написать `HttpEngineFactory` под wasm (Js-движок).
5. Добавить таргет во ВСЕ модули одинаково:
   ```
   @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
   wasmJs { browser() ; binaries.executable() }
   ```
   и поднять `wasmJsMain`-сорссеты, где нужны платформенные actual (network, datastore, files, biometry).
6. biometry на wasm: actual = `isAvailable() = false`, `NotAvailable` (как desktop).
7. files на wasm: заглушка или js-реализация скачивания.
8. composeApp: добавить `wasmJs { browser() }`, перенести точку входа в `wasmJsMain`
   (сейчас лежит в `webMain` — переименовать/перенести), запуск:
   ```
   ./gradlew :composeApp:wasmJsBrowserDevelopmentRun
   ```

## Итог
wasm — не правка одного файла, а апгрейд koin 4 + settings + абстракция хранилища +
js-движок ktor, с проверкой сборкой на каждом шаге. Делать отдельным заходом.
