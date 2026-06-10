# EventMap

Карта живых событий и людей. Compose Multiplatform приложение (Android / iOS / Desktop) с картой, событиями, подписками на организаторов и жанры, живыми видео-отзывами и социальной лентой «человек как событие».

**Старт проекта:** 19 мая 2026
**Релиз 1.0:** 18 мая 2027

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
├── 02_Code/                ← Compose Multiplatform проект
│   ├── shared/             общий KMP код
│   │   ├── commonMain/
│   │   ├── androidMain/
│   │   ├── iosMain/
│   │   └── desktopMain/
│   ├── androidApp/
│   ├── iosApp/
│   └── desktopApp/
│
├── 03_UI_UX/               ← Дизайн
│   ├── wireframes/         lo-fi
│   ├── mockups/            hi-fi + EventMap-prototype.html
│   ├── design_system/      цвета, типографика, компоненты
│   └── user_flows/         потоки пользователя
│
├── 04_Backend/             ← Ktor server
│   ├── api/
│   ├── database/           PostgreSQL + PostGIS схемы
│   ├── auth/               JWT, OAuth
│   ├── storage/            S3 / R2 для фото и видео
│   └── push_notifications/ FCM + APNS
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
- **Все экраны (67):** [03_UI_UX/SCREENS.md](03_UI_UX/SCREENS.md)
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
