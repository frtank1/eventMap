# Деплой бэкенда EventMap

Бэк портабельный: всё, что нужно для запуска, — это Docker-образ (`04_Backend/server/Dockerfile`)
и переменные окружения. Поэтому он одинаково едет на Render, Railway, Fly, VPS или k8s —
меняется только то, *кто* запускает контейнер и подставляет переменные.

## Три окружения

| Окружение | Где | Ветка | Когда |
|-----------|-----|-------|-------|
| **test** | локально / CI | любая | разработка, прогон тестов |
| **staging (pre-prod)** | Render | `staging` | проверка перед prod |
| **prod** | Render | `main` | боевое |

### test — локально
```bash
cd 04_Backend/server
docker compose up --build      # поднимет Postgres + бэк на localhost:8080
```
В CI (`.github/workflows/ci.yml`) на каждый push гоняются `./gradlew build` (тесты).

### staging / prod — Render (Blueprint)
Вся инфраструктура описана в `render.yaml` в корне репо (Infrastructure as Code):
два web-сервиса + две базы Postgres, переменные и привязка к веткам.

Подключение (один раз):
1. Запушь репо на GitHub (монорепо целиком — Render берёт бэк из `rootDir: 04_Backend/server`).
2. Render → **New → Blueprint** → выбери репозиторий → Apply.
3. Render создаст обе базы и оба сервиса. `JWT_SECRET` и `ADMIN_KEY` генерируются автоматически.
4. Впиши вручную `CORS_HOSTS` в обоих сервисах (домены фронта/приложения через запятую) —
   без него prod не стартует (защита в `AppConfig`).

Дальше деплой автоматический:
- push в `staging` → обновляется pre-prod;
- push в `main` → обновляется prod.

## Git-flow

```
feature/* → (PR) → staging → (проверил) → main
```
- работаешь в `feature/...`, мёржишь в `staging`;
- Render катит staging, тестируешь на живом URL;
- мёржишь `staging` → `main`, Render катит prod.

## Переменные окружения

| Переменная | Назначение | Источник |
|-----------|-----------|----------|
| `APP_ENV` | `dev` / `staging` / `prod` | render.yaml |
| `DATABASE_URL` | `postgres://user:pass@host:port/db` | из базы (Render подставляет) |
| `JWT_SECRET` | секрет JWT | генерируется |
| `ADMIN_KEY` | админ-операции | генерируется |
| `CORS_HOSTS` | домены фронта через запятую | вручную |
| `PORT` | порт (8080) | render.yaml / хост |

`AppConfig` понимает и `DATABASE_URL` (один URL, стандарт всех хостингов), и раздельные
`DB_URL` / `DB_USER` / `DB_PASSWORD` (для docker-compose/локалки). Приоритет: раздельные → `DATABASE_URL` → локальный дефолт.

## Переезд на другой хост

Образ и конфиг от платформы не зависят. Чтобы уехать с Render:
1. Любой хост, умеющий Docker, собирает `04_Backend/server/Dockerfile`.
2. Подсунь ему `DATABASE_URL`, `JWT_SECRET`, `ADMIN_KEY`, `CORS_HOSTS`, `APP_ENV`, `PORT`.
3. Открой порт 8080. Flyway сам прогонит миграции при старте.

Здоровье сервиса — `GET /health` → `200 OK` (без БД и авторизации).

## Подключение приложения

В `02_Code/composeApp/.../PlatformSdk.kt`:
```kotlin
baseUrl = "https://<staging-или-prod-url>.onrender.com/",   // без /v1, слэш в конце обязателен
```
Сервер отдаёт роуты с корня (`/auth`, `/events`) — префикса `/v1` нет.
HTTPS — значит ATS-послабления в `iosApp/.../Info.plist` можно убрать.
