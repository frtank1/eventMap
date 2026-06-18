# EventMap — Спека для разработки (Compose)

Источник дизайна — Figma; текстовые описания экранов — `03_UI_UX/screens/`. Дизайн-система **Aurora Violet**, ночная тема. Цель: перенести токены, компоненты и навигацию в KMP/Compose-проект.

---

## 1. Дизайн-токены

### Цвета (Color.kt)

```kotlin
// Brand
val Primary      = Color(0xFF7C5CFF)
val PrimaryDark  = Color(0xFF6644E0)
val PrimaryLight = Color(0xFFA78BFF)
val Accent       = Color(0xFFFF5C8A)
val AccentDark   = Color(0xFFE04A75)

// Semantic
val Live    = Color(0xFFFF4757)
val Soft    = Color(0xFF5DCAA5)   // success / positive
val Warning = Color(0xFFFAC775)
val Danger  = Color(0xFFE24B4A)
val Info    = Color(0xFF85B7EB)

// Surfaces (night)
val Bg        = Color(0xFF0A1420)
val Surface   = Color(0xFF11212F)
val SurfaceV  = Color(0x12FFFFFF)  // rgba(255,255,255,.07) — поверх поверхностей

// Текст
val Text   = Color(0xFFFFFFFF)
val TextOn = Color(0xFFE9ECF5)     // основной текст на фоне
val Text2  = Color(0xFF7E8D9F)     // вторичный
val Text3  = Color(0xFF56657A)     // третичный / подписи

// Карта (ночь)
val MapBg    = Color(0xFF0C1825)
val MapRoad  = Color(0xFF1A2A3D)
val MapPark  = Color(0xFF0F2419)
val MapWater = Color(0xFF0A2540)
val MapBuild = Color(0xFF11212F)
```

### Градиенты

```kotlin
val GradBrand = Brush.linearGradient(listOf(Color(0xFF7C5CFF), Color(0xFFFF5C8A))) // 135°
val GradLive  = Brush.linearGradient(listOf(Color(0xFFFF4757), Color(0xFFFF5C8A))) // 135°
// Фон экрана: radial 1200×800 at 50% -10%: #1A1140 → #070B14 (60%)
```

### Радиусы

```kotlin
val RadiusSm = 8.dp
val RadiusMd = 12.dp    // кнопки, инпуты, item-карточки
val RadiusLg = 16.dp    // карточки секций
val RadiusXl = 24.dp    // bottom-sheet / крупные блоки
val RadiusPill = 999.dp // чипы, поиск, бейджи
val PhoneFrame = 34.dp  // только для макета телефона
```

### Типографика — Manrope (weights 400/500/600/700/800)

| Роль | Размер | Вес |
|---|---|---|
| H1 / экран-заголовок | 26 | 800 |
| Sheet title | 19–22 | 800 |
| Section header (uppercase, letterspacing .5) | 11 | 800 |
| Item / body | 13 | 600 |
| Meta / вторичный | 11–12 | 500–600 |
| Caption / код ошибки | 10–11 | 700 |
| Button | 14 | 800 |

### Отступы / сетка

Базовый горизонтальный padding контента — **16dp**. Между секциями — 14dp сверху. Карточки-айтемы: padding 11–13dp, gap 11dp. Bottom-sheet: padding 18dp по бокам, 22dp снизу. Иконка-чип в item: 32×32 (radius 9), в крупных rows: 34–46.

---

## 2. Каталог компонентов

| Компонент | Где | Описание |
|---|---|---|
| `PhoneStatusBar` | все | системная строка (макетная) |
| `TopSearchBar` | C | лого-чип + pill-поиск, blur-фон |
| `MapPin` | C | dot 46×46 + label-pill; варианты `soon`(градиент), `live`(пульс+градиент live), `person`(аватар) |
| `BottomSheet` | C4/C5/C7/D5 | radius 22 сверху, grip 40×4, тени `0 -10 40` |
| `Badge` | C, J | pill-чип; `soon`(тинт primary), `live`(градиент+пульс-точка) |
| `VideoCircle` | C5 | 54×54 radius 14, border live, бейдж ▶ |
| `PrimaryButton` | все | градиент brand, radius 13, вес 800, тень `0 8 20 primary/.35` |
| `LiveButton` | C5/C6 | вариант с градиентом live |
| `GhostButton` | все | фон SurfaceV, текст #cfd6ea |
| `DangerButton` | I4, J | тинт danger/.14, текст danger |
| `ListItem` | I, F, H | ic-чип + tx + (val) + arrow `›`; вариант `danger` |
| `ToggleSwitch` | I, H | 42×24 pill, on=primary / off=#33415a |
| `RadioReason` | J1 | rb 20×20 + текст; on → залит primary |
| `Tabs` | D, F, G, H | подчёркивание активного primary 2px |
| `TextField` | A, I | фон Surface radius 12, placeholder Text3, eye-иконка |
| `Chip / InterestChip` | A8, G | выбираемый pill |
| `EmptyState` | J3, K1–K4 | emoji 64 + h3 + p + primary + ghost |
| `EventReel` | D1a | фуллскрин-рилс 9:16 (TikTok-style), свайп ↕ между событиями, реакции справа |
| `EventSheet` | D1a/D1b | шторка снизу (grab-handle): свёрнута = чипы+CTA, потянул вверх = полное описание |
| `GeoCtaButton` | D1c | состояния: «Приду» (до старта, free) / «Купить» (платное) / «Я тоже здесь» (идёт, гео ≤300м) / disabled (идёт, далеко) |
| `FeedContextPill` | D1a | пилюля порядка ленты: похожие → сегодня (утро→вечер) → завтра… или по фильтру C8 |
| `MetaRow` | D1b | ic-чип 34 + текст |
| `BizPin` | M4, C | пин бизнеса: логотип в кружке, цвет подсветки, анимация категории, пульс-кольцо |
| `AnimPicker` | M4 | сетка анимаций пина, отфильтрована по категории бизнеса (наборы грузит админ) |
| `QuestCard` | Q1 | карточка игры: бренд-градиент, условия, награда-пилюля |
| `QuestMapRoute` | Q2/Q5 | мини-карта с точками (✓/активная/🔒) и пунктирным маршрутом |
| `CheckinScanner` | Q3 | рамка-сканер QR + переключатель типа проверки (гео/фото/QR) |
| `PrizeQR` | Q4/P2 | карточка приза/билета с QR |
| `PulseGlow` | C2.0 | радиальное свечение события, диаметр/яркость ∝ людям (G1) |
| `CrowdDots` | C2.0 | анонимные точки-люди, анимация стекания к событию (G2/G9) |
| `FogLayer` | C2.0 | туман войны над неисследованными районами + % исследования (G3) |
| `BeaconBeam` | C2.0 | столб света мега-ивента (G5) |
| `QuestCompass` | C1/C2.0 | пилюля-компас «движ по вкусу · N м →» (G6) |
| `EmberTrace` | C1/C2.0 | остывающий след события: тусклое свечение + 🔥, тап → галерея (G4) |
| `ForecastZone` | C2.0 | пунктирная зона прогноза «через час будет жарко» (G8) |
| `WaveRipple` | C2.0 | концентрические кольца city-wide вспышки (G7) |
| `GhostPin` | C2.0 | пунктирный пин секретного дропа, виден ≤500 м, таймер (G11) |
| `XPBar` / `LevelPill` | R1/H1 | уровень, прогресс XP, титул |
| `BadgeChip` | R1/H1/Q4 | значок-ачивка (🍔 Бургер-босс и т.п.) |
| `SeasonPassTrack` | R1 | прогресс пасса + слоты лута от бизнесов (G13) |
| `MissionCard` | R1 | миссия с наградой, в т.ч. совместная 2×XP (G14) |
| `TimeCapsuleSheet` | R3 | шторка капсулы: фото/текст + получатель (G15) |
| `FriendItem` | N1 | друг со статусом присутствия («на событии», «рядом») + 💌 |
| `InviteTemplateCard` | N2/N4 | анимированная открытка-шаблон по типу праздника |
| `GuestPicker` | N3 | чек-лист друзей + группы-чипы + ссылка/QR для не-юзеров |
| `RsvpButtons` | N4 | «✓ Приду» / «Не смогу» + календарь |
| `RsvpDashboard` | N5 | статы + список гостей со статус-пилюлями |
| `StatusPill` | J2 | `rev`(warning) / `done`(soft) / `rej`(серый) |
| `LivePlayerOverlay` | C6/E2 | top(аватар+views) + side(actions) + bottom(caption+cta) |
| `RecordControls` | E3 | таймер-pill, shutter 74 (border 5, in 30 radius 8) |
| `BottomNav` | основной каркас | центр = запись видео (FAB) |

Permission-экраны (B): `illu` с концентрическими кольцами + emoji 84, объяснение, bullets, primary + «Не сейчас», поверх — макет системного диалога.

---

## 3. Карта экранов и навигация

89 экранов, разделы A–M, P, Q, R (геймификация). Реестр статусов — `SCREENS.md`. Hi-fi и прототипы — в Figma (источник правды): https://www.figma.com/design/Lf0iBDnxz7x80uA4LAAyDJ. Горячий путь также в `mockups/PROTOTYPE_hotpath.html` (легаси).

Ключевые правила D-раздела: карточка события = рилс; порядок свайпа = похожие → сегодня → завтра (или фильтр C8); CTA по времени/гео/цене (см. D1c); 18+ тумблер в D8 → бейдж на рилсе и скрытие у несовершеннолетних; платное = только «Купить» (покупка = «иду», билеты друзьям: с аккаунтом — видны, без — учтены анонимно «+N»).

### Главные переходы

```
A1 splash → A2→A3→A4 welcome → A5/A6 (рег/логин) → A8 интересы
        → B1→B2→B3 разрешения → C1 карта

C1 карта ─тап пин─┬─ soon  → C4 sheet → D1 карточка → D2 галерея → D4 медиа
                  │                              └ D5 добавить медиа → E3→E4 публикация
                  ├─ live  → C5 sheet → C6 плеер
                  │                 └ «Я тоже здесь» → E3 запись → E4 превью → публикация
                  └─ person→ C7 sheet → H2 чужой профиль

BottomNav центр → E3 запись → E4 превью → публикация
C8 фильтры · C9 спонсор · C10 люди · C11 тепло · C12 пусто

H1 мой профиль → H3 редакт · H4 история · H7 приватность · H11 Sharing
H8 организатор → H9 редакт · H10 верификация

F1 подписки → F2 лента · F3 запросы · F4 уведомления · F5 настройки
G1 поиск → G2 результаты → G3 жанр
I1 настройки → I2 приватность · I3 уведомления · I4 аккаунт · I5 о приложении
J1 жалоба · J2 мои жалобы · J3 бан    K1 нет сети · K2 нет событий · K3 нет подписок · K4 ошибка
```

### Граф навигации (JSON — для роутера/sealed-class Destination)

```json
{
  "graphs": {
    "auth":    ["A1","A2","A3","A4","A5","A6","A7","A8"],
    "perms":   ["B1","B2","B3"],
    "map":     ["C1","C2","C3","C4","C5","C6","C7","C8","C9","C10","C11","C12"],
    "event":   ["D1","D2","D3","D4","D5","D6","D7","D8"],
    "video":   ["E1","E2","E3","E4","E5"],
    "subs":    ["F1","F2","F3","F4","F5"],
    "search":  ["G1","G2","G3"],
    "profile": ["H1","H2","H3","H4","H5","H6","H7","H8","H9","H10","H11"],
    "settings":["I1","I2","I3","I4","I5"],
    "moderation":["J1","J2","J3"],
    "errors":  ["K1","K2","K3","K4"],
    "business":["M1","M2","M3","M4","M5","M6"],
    "payments":["P1","P2","P3","P4"],
    "quests":["Q1","Q2","Q3","Q4","Q5","Q6"],
    "game":["R1","R2","R3"],
    "friends":["N1","N2","N3","N4","N5"]
  },
  "edges": [
    {"from":"A8","to":"B1"},{"from":"B3","to":"C1"},
    {"from":"C1","to":"C4","trigger":"pin_soon"},
    {"from":"C1","to":"C5","trigger":"pin_live"},
    {"from":"C1","to":"C7","trigger":"pin_person"},
    {"from":"C4","to":"D1"},{"from":"C5","to":"C6"},
    {"from":"C5","to":"E3","trigger":"im_here"},
    {"from":"C7","to":"H2"},
    {"from":"D1","to":"D2"},{"from":"D2","to":"D4"},
    {"from":"D1","to":"D5"},{"from":"D5","to":"E3"},
    {"from":"E3","to":"E4"},{"from":"E4","to":"publish"},
    {"from":"H1","to":"H3"},{"from":"H1","to":"H4"},
    {"from":"H1","to":"H7"},{"from":"H1","to":"H11"},
    {"from":"H8","to":"H9"},{"from":"H8","to":"H10"},
    {"from":"G1","to":"G2"},{"from":"G2","to":"G3"},
    {"from":"F1","to":"F2"},{"from":"F1","to":"F3"},
    {"from":"I1","to":"I2"},{"from":"I1","to":"I3"},{"from":"I1","to":"I4"},{"from":"I1","to":"I5"},
    {"from":"H1","to":"M1","trigger":"become_business"},
    {"from":"M1","to":"M2"},{"from":"M2","to":"M3"},{"from":"M3","to":"M4"},{"from":"M4","to":"M5"},{"from":"M5","to":"M6"},
    {"from":"D1","to":"P1","trigger":"buy_ticket"},{"from":"P1","to":"P2"},{"from":"P1","to":"P3"},{"from":"P2","to":"P4","trigger":"after_event"},
    {"from":"C1","to":"Q1","trigger":"quests_nearby"},{"from":"Q1","to":"Q2"},{"from":"Q2","to":"Q3","trigger":"checkin"},{"from":"Q3","to":"Q4","trigger":"last_point"},
    {"from":"M1","to":"Q5","trigger":"create_quest"},{"from":"Q5","to":"Q6","trigger":"published"},
    {"from":"H1","to":"R1","trigger":"level_pill"},{"from":"R1","to":"Q1"},{"from":"R1","to":"R2","trigger":"december"},{"from":"C1","to":"R3","trigger":"long_press_place"},
    {"from":"H1","to":"N1","trigger":"friends"},{"from":"N1","to":"N2","trigger":"create_invite"},{"from":"N2","to":"N3"},{"from":"N3","to":"N4","trigger":"sent_preview"},{"from":"N4","to":"N5","trigger":"organizer_view"}
  ],
  "bottomNav": {"items":["C1","E1","centerFab=E3","F2","H1"]}
}
```

---

## 4. Карта (ключевой паттерн)

Карта = нативный MapView (Mapbox SDK через `expect/actual`, план B — MapLibre) + Compose-overlay поверх. Live-объекты (пины, кружки видео, sharing-аватары, тепло) рисуются на Compose `Canvas`; каждый кадр перепроецируем `LatLng → screen Offset`. Подробности — `05_Docs/ARCHITECTURE.md`.

---

*Источник истины по экранам — `SCREENS.md`. Визуальный референс — галереи в `mockups/` + `index.html`. Обновлено 31.05.2026.*
