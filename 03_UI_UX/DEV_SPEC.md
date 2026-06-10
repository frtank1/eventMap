# EventMap — Спека для разработки (Compose)

Извлечено из hi-fi макетов (`03_UI_UX/mockups/`). Дизайн-система **Aurora Violet**, ночная тема. Цель: перенести токены, компоненты и навигацию в KMP/Compose-проект.

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
| `EventCover` | D1 | 200dp, градиент-оверлей, заголовок снизу |
| `MetaRow` | D1 | ic-чип 34 + текст |
| `StatusPill` | J2 | `rev`(warning) / `done`(soft) / `rej`(серый) |
| `LivePlayerOverlay` | C6/E2 | top(аватар+views) + side(actions) + bottom(caption+cta) |
| `RecordControls` | E3 | таймер-pill, shutter 74 (border 5, in 30 radius 8) |
| `BottomNav` | основной каркас | центр = запись видео (FAB) |

Permission-экраны (B): `illu` с концентрическими кольцами + emoji 84, объяснение, bullets, primary + «Не сейчас», поверх — макет системного диалога.

---

## 3. Карта экранов и навигация

67 экранов, разделы A–K. Реестр статусов — `SCREENS.md`. Горячий путь интерактивно собран в `mockups/PROTOTYPE_hotpath.html`.

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
    "errors":  ["K1","K2","K3","K4"]
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
    {"from":"I1","to":"I2"},{"from":"I1","to":"I3"},{"from":"I1","to":"I4"},{"from":"I1","to":"I5"}
  ],
  "bottomNav": {"items":["C1","E1","centerFab=E3","F2","H1"]}
}
```

---

## 4. Карта (ключевой паттерн)

Карта = нативный MapView (Mapbox SDK через `expect/actual`, план B — MapLibre) + Compose-overlay поверх. Live-объекты (пины, кружки видео, sharing-аватары, тепло) рисуются на Compose `Canvas`; каждый кадр перепроецируем `LatLng → screen Offset`. Подробности — `05_Docs/ARCHITECTURE.md`.

---

*Источник истины по экранам — `SCREENS.md`. Визуальный референс — галереи в `mockups/` + `index.html`. Обновлено 31.05.2026.*
