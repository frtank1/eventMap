# EventMap server (Ktor, Clean Architecture)

Бэкенд по слоям, PostgreSQL + Exposed, DI на Koin, JWT-аутентификация. Запускается локально через Docker; готов к деплою (Dockerfile).

## Слои (зависимости только внутрь)

```
presentation/   HTTP: routes + DTO (+мапперы domain→dto)   ──┐
        │ зависит от                                          │
domain/         model + repository (порты) + usecase          │  чистый, без фреймворков
        ▲ реализуется в                                       │
data/           Exposed-таблицы, DatabaseFactory, repo-impl  ──┘
security/       JwtService          di/  Koin-модуль          config/  env-конфиг
```

Правило: `domain` ничего не знает о Ktor/Exposed. `data` и `presentation` реализуют/используют порты из `domain`. Подмена БД или HTTP-слоя не трогает бизнес-логику.

## Запуск (локально, через Docker)

```bash
cd 04_Backend/server
docker compose up --build
```

Поднимутся Postgres + сервер на `http://localhost:8080`. При первом старте создаются таблицы и заливается сид по Астане.

Проверка:
```bash
curl http://localhost:8080/events
curl -X POST http://localhost:8080/auth/register -H "Content-Type: application/json" \
  -d '{"email":"a@a.kz","password":"123456","name":"Азамат"}'
```

### Без Docker (если Postgres уже стоит локально)
Создай БД `eventmap` (user/pass `eventmap`/`eventmap`) или переопредели env (`DB_URL`, `DB_USER`, `DB_PASSWORD`), затем:
```bash
./gradlew run
```

## Эндпоинты

- `POST /auth/register` · `/auth/login` → `AuthResponse` (JWT access+refresh). Пароли хешируются bcrypt.
- `GET /events` · `/events/{id}` · участники/медиа/рейтинги · attend/rating/purchase.
- `GET /organizers/{id}` · `/organizers/{id}/events` · subscribe.

## Подключить приложение
В `02_Code/.../PlatformSdk.kt` → `CoreConfig.baseUrl = "http://10.0.2.2:8080/"` (Android-эмулятор) и убрать dev-байпас в `AuthRepositoryImpl`.

## Деплой (позже)
`Dockerfile` собирает fat-образ. На Railway/Render/Fly: подключить репозиторий, задать env (`DB_URL`, `JWT_SECRET`), привязать managed Postgres. Локальная структура и Docker уже готовы к этому.
