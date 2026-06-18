# EventMap

Карта живых событий и людей. Compose Multiplatform приложение (Android / iOS / Desktop) с картой, событиями, подписками на организаторов и жанры, живыми видео-отзывами и социальной лентой «человек как событие».

**Старт проекта:** 19 мая 2026
**Релиз 1.0:** 18 мая 2027

> **Статус (июнь 2026):** проект опережает roadmap — каркас всех экранов (A–P) и бэкенд
> с auth/events уже собраны. Актуальное состояние: [05_Docs/PROJECT_REVIEW_2026-06-09.md](05_Docs/PROJECT_REVIEW_2026-06-09.md),
> статус кода: [02_Code/README.md](02_Code/README.md).
>
> **Ревизия дизайна (11.06.2026):** карточка события = фуллскрин-рилс (TikTok-style) со шторкой;
> CTA «Приду/Купить/Я тоже здесь» (время+гео); 18+; порядок ленты (похожие → сегодня → завтра / фильтр);
> покупка билетов друзьям (аккаунт/анонимно); раздел M — бизнес-аккаунт юрлица (БИН+модерация,
> пин с анимациями по категории); раздел Q — промо-квесты от бизнеса (гео/фото/QR-чекины, призы);
> «Живая карта 2.0» — 16 игровых механик G1–G16 (пульс города, поток людей, туман войны, спонтанный
> пульс и др., MAP_CONCEPT §10–11); раздел R — геймификация (игровой хаб с XP/значками/сезонным
> пассом/миссиями, Wrapped «Твой год в городе», капсулы времени); раздел N — друзья и приглашения
> (приватные свадьбы/ДР с анимированными открытками и RSVP, гости по ссылке/QR). Итого 94 экрана.
>
> **Бэкенд (11.06.2026):** миграции V2–V5 под все новые домены; реализованы рилс-соцслой
> (комментарии/лайки/колокольчик), честное присутствие с гео-проверкой (D1c), билеты
> с гостями (авто-«иду», анонимный «+N», QR, чек-ин организатором), бизнес-аккаунты
> (БИН-заявка, модерация, анимации пина строго своей категории), промо-квесты
> (чек-ины geo/фото/HMAC-QR, цепочки, XP, приз), друзья/приглашения (RSVP, веб-открытка
> без аккаунта, приватные события скрыты с карты) и геймификация (профиль XP/уровни/значки,
> туман войны, лидерборд районов, капсулы времени с гео-открытием) и пульс карты
> (анонимные пинги с HMAC и TTL, glow-агрегаты, детект аномалий G9 фоновой джобой).
> **Контракт §8 закрыт: 7 доменов, 40 эндпоинтов.**
> Статус: [04_Backend/server/README.md](04_Backend/server/README.md).
>
> **Дизайн в Figma (источник правды):** https://www.figma.com/design/Lf0iBDnxz7x80uA4LAAyDJ —
> ~64 hi-fi экрана на 11 страницах с кликабельными прототипами и Smart Animate.
> Текстовые описания всех экранов: [03_UI_UX/screens/](03_UI_UX/screens/).
> Детали: [03_UI_UX/SCREENS.md](03_UI_UX/SCREENS.md), бэклог: EPIC 12–15 в [01_PM/BACKLOG.md](01_PM/BACKLOG.md).

---

## Структура репозитория

```
EventMap/
├── 01_PM/                  ← PM: roadmap, backlog, отчёты, спринты
│   ├── ROADMAP.md          12-месячный план
│   ├── BACKLOG.md          все фичи с приоритетами P0/P1/P2
│   ├── templates/          шаблоны дневных и недельных отчётов
│   ├── sprints/            недельные спринт-обзоры
│   └── reports/            365 дневных отчётов по 12 месяцам
│       ├── month_01/       (19–31 мая 2026)
│       ├── month_02/       (июнь 2026)
│       └── ...month_12/    (апрель — 18 мая 2027)
│
├── 02_Code/                ← Compose Multiplatform проект (Android/iOS/Desktop/Wasm)
│   ├── composeApp/         точка входа всех платформ: App(), MainScreen, PlatformSDK
│   ├── common/
│   │   ├── core/           ядро: network, datastore, ui (тема+ui-kit), utils, logger…
│   │   └── feature/        фичи (auth, map, event, feed, search, profile, video),
│   │                       каждая = domain / data / ui модули
│   ├── iosApp/             обёртка под Xcode
│   └── build-logic/        convention-плагины Gradle
│
├── 03_UI_UX/               ← Дизайн
│   ├── wireframes/         lo-fi
│   ├── screens/            текстовые описания всех экранов
│   ├── mockups/            рабочие прототипы (Leaflet, hotpath)
│   ├── design_system/      цвета, типографика, компоненты
│   └── user_flows/         потоки пользователя
│
├── 04_Backend/             ← Ktor server
│   └── server/             auth (JWT + refresh-ротация), events, organizers;
│                           Exposed + PostgreSQL, Flyway-миграции, docker-compose
│
├── 05_Docs/                ← Документация
│   └── ARCHITECTURE.md
│
├── 06_Resources/           ← Ассеты
│   ├── icons/
│   ├── images/
│   ├── fonts/
│   └── videos/
│
├── 07_Testing/             ← Тесты и баги
│   ├── unit/
│   ├── ui/
│   ├── e2e/
│   └── bug_reports/
│
└── 08_Marketing/           ← Маркетинг для релиза
    ├── landing/
    ├── social/
    └── press_kit/
```

---

## Быстрая навигация

- **План работы:** [01_PM/ROADMAP.md](01_PM/ROADMAP.md)
- **Список всех фич:** [01_PM/BACKLOG.md](01_PM/BACKLOG.md)
- **Архитектура:** [05_Docs/ARCHITECTURE.md](05_Docs/ARCHITECTURE.md)
- **Шаблон дневного отчёта:** [01_PM/templates/daily_report_template.md](01_PM/templates/daily_report_template.md)
- **Дневные отчёты:** [01_PM/reports/](01_PM/reports/)
- **Прототип:** [03_UI_UX/mockups/EventMap-prototype.html](03_UI_UX/mockups/EventMap-prototype.html)
- **Все экраны (94):** [03_UI_UX/SCREENS.md](03_UI_UX/SCREENS.md)
- **Дизайн в Figma:** https://www.figma.com/design/Lf0iBDnxz7x80uA4LAAyDJ
- **Design tokens (Aurora Violet):** [03_UI_UX/design_system/DESIGN_TOKENS.md](03_UI_UX/design_system/DESIGN_TOKENS.md)
- **Технологический стек:** [05_Docs/TECH_STACK.md](05_Docs/TECH_STACK.md)
- **Концепция «живой карты»:** [05_Docs/MAP_CONCEPT.md](05_Docs/MAP_CONCEPT.md)

---

## Как использовать структуру каждый день

1. Утром: открой текущий день в `01_PM/reports/month_NN/day_NNN_YYYY-MM-DD.md`, запиши план.
2. В течение дня: фиксируй коммиты и блокеры там же.
3. Вечером: закрой день — отметь `[x]`, перечисли план на завтра.
4. По пятницам: создай файл недели в `01_PM/sprints/` по шаблону `weekly_sprint_template.md`.
5. В конце каждого `month_NN`: заполни секцию «Цели месяца» и «Definition of Done» в его README.
