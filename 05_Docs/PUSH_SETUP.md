# Пуши в EventMap: что уже работает и как включить FCM

## Локальные напоминания — работают из коробки (07.07.2026)

Без Firebase и бэка: «Я пойду» / покупка билета / колокольчик в рилсе планируют
локальное уведомление **за час до старта** (Android — WorkManager, iOS — UNUserNotificationCenter).
Отмена колокольчика снимает напоминание. Ничего настраивать не нужно.

## FCM (серверные пуши: «подписка запостила LIVE» и т.п.) — 6 шагов руками

Бэк уже готов: `POST /push/token` принимает токены, `PushRoutes` шлёт через FCM,
но живёт в режиме no-op, пока нет ключа. Что сделать:

1. **Firebase-проект**: console.firebase.google.com → Add project → «EventMap».
2. **Android-приложение** в проекте: package `com.eventmap` (сверь с applicationId
   в `02_Code/androidApp/build.gradle.kts`) → скачай `google-services.json` →
   положи в `02_Code/androidApp/`.
3. **Gradle**: в `02_Code/build.gradle.kts` (root) добавь classpath/plugin
   `com.google.gms.google-services`, в `androidApp/build.gradle.kts` — сам плагин
   и `implementation("com.google.firebase:firebase-messaging:24.x")`.
   ВАЖНО: без `google-services.json` сборка с плагином падает — поэтому плагин
   не добавлен заранее.
4. **Клиент**: сервис `FirebaseMessagingService` (onNewToken → `POST /push/token`).
   Скажи мне, когда json будет на месте, — напишу сервис и регистрацию токена.
5. **Сервисный ключ для бэка**: Firebase console → Project settings →
   Service accounts → Generate new private key (JSON).
6. **Render**: env-переменная с этим JSON (см. имя в `PushRoutes`/`AppConfig` —
   `FCM_*`), redeploy. После этого `sendPush` перестанет быть no-op.

Порядок работ: шаги 1–2 и 5–6 — твои (аккаунты/ключи), 3–4 — мои по команде.
