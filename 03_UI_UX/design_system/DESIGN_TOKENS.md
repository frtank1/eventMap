# EventMap — Design Tokens (Aurora Violet)

Финальная палитра, утверждена 20.05.2026. Все значения готовы к использованию в Compose Multiplatform `MaterialTheme` + кастом-токенах.

## 1. Цвета

### Brand
| Token | HEX | Назначение |
|---|---|---|
| `Primary` | `#7C5CFF` | Главный бренд, активный таб, FAB локации, CTA |
| `PrimaryDark` | `#6644E0` | Pressed state, hover |
| `PrimaryLight` | `#A78BFF` | Disabled state, second-tier |
| `Accent` | `#FF5C8A` | Градиент с Primary (CTA «Снять момент», нав-кнопка) |
| `AccentDark` | `#E04A75` | Pressed state |

### Семантические
| Token | HEX | Назначение |
|---|---|---|
| `Live` | `#FF4757` | LIVE-бэйдж, кружки видео, индикаторы трансляции |
| `Soft` | `#5DCAA5` | Подтверждение, «идёт», soft-аватары, чек-марки |
| `Warning` | `#FAC775` | «Скоро», окна зданий ночью, тёплые акценты |
| `Danger` | `#E24B4A` | Ошибки, отмена |
| `Info` | `#85B7EB` | Информационные плашки |

### Фоны и поверхности — Night theme
| Token | HEX | Назначение |
|---|---|---|
| `MapBg` | `#0c1825` | Фон карты ночью |
| `MapRoad` | `#1a2a3d` | Улицы |
| `MapPark` | `#0f2419` | Парки |
| `MapWater` | `#0a2540` | Вода |
| `MapBuilding` | `#11212f` | Здания |
| `Background` | `#0a1420` | App background |
| `Surface` | `#11212F` | Карточки, sheet |
| `SurfaceVariant` | `rgba(255,255,255,0.07)` | Поверх карты (search) |

### Фоны и поверхности — Day theme
| Token | HEX | Назначение |
|---|---|---|
| `MapBg` | `#E8F4FB` | Фон карты днём |
| `MapRoad` | `#D6E0EA` | Улицы |
| `MapPark` | `#B8DCC7` | Парки |
| `MapWater` | `#9BCDE8` | Вода |
| `MapBuilding` | `#FFFFFF` | Здания |
| `Background` | `#FAFAFC` | App background |
| `Surface` | `#FFFFFF` | Карточки, sheet |
| `SurfaceVariant` | `#F1EFE8` | Поверх карты (search) |

### Текст
| Token | Dark mode | Light mode | Назначение |
|---|---|---|---|
| `TextPrimary` | `#FFFFFF` | `#0c1825` | Основной |
| `TextSecondary` | `#7e8d9f` | `#6B7280` | Подпись |
| `TextTertiary` | `#56657A` | `#9CA3AF` | Хинты |

### Градиенты
- **BrandGradient** = `linear-gradient(135deg, #7C5CFF, #FF5C8A)` — центр-кнопка нав, FAB записи, аватары
- **LiveGradient** = `linear-gradient(135deg, #FF4757, #FF5C8A)` — карточки LIVE
- **SkyDayGradient** = `linear-gradient(180deg, #B8E0F5, #E8F4FB)` — небо днём
- **SkyNightGradient** = `linear-gradient(180deg, #0a1420, #0c1825)` — небо ночью

## 2. Типографика

Шрифт: **Manrope** (свободная лицензия, отличная кириллица, поддержка казахского). Fallback: `system-ui, -apple-system, "SF Pro Display"`.

| Style | Size | Weight | Line height | Применение |
|---|---|---|---|---|
| `DisplayLarge` | 32 | 700 | 40 | Hero на онбординге |
| `DisplayMedium` | 24 | 600 | 32 | Заголовки экранов |
| `TitleLarge` | 18 | 600 | 24 | Заголовки секций |
| `TitleMedium` | 16 | 600 | 22 | Карточки |
| `BodyLarge` | 15 | 400 | 22 | Описание события |
| `BodyMedium` | 13 | 400 | 18 | Подписи в UI |
| `LabelLarge` | 13 | 500 | 16 | Кнопки |
| `LabelMedium` | 11 | 500 | 14 | Пиллы, бейджи |
| `LabelSmall` | 9 | 500 | 12 | LIVE / СКОРО / ИДЁТ |

## 3. Spacing

Базовая шкала: 4dp. Кратные: 4, 8, 12, 16, 20, 24, 32, 48, 64.

| Token | dp |
|---|---|
| `xs` | 4 |
| `sm` | 8 |
| `md` | 12 |
| `lg` | 16 |
| `xl` | 24 |
| `xxl` | 32 |

## 4. Скругления

| Token | dp | Применение |
|---|---|---|
| `RadiusXs` | 4 | Маленькие пилы, плашки |
| `RadiusSm` | 8 | Кнопки, карточки списка |
| `RadiusMd` | 12 | Карточки события |
| `RadiusLg` | 16 | Bottom sheet |
| `RadiusXl` | 24 | Bottom sheet top |
| `RadiusFull` | 999 | Avatar, FAB, пилы статуса |

## 5. Compose: ColorScheme и Theme

```kotlin
// Color.kt
val Violet600 = Color(0xFF7C5CFF)
val Violet700 = Color(0xFF6644E0)
val Violet400 = Color(0xFFA78BFF)
val Coral500 = Color(0xFFFF5C8A)
val Coral600 = Color(0xFFE04A75)

val Live = Color(0xFFFF4757)
val Soft = Color(0xFF5DCAA5)
val Warning = Color(0xFFFAC775)

// Day
val DayMapBg = Color(0xFFE8F4FB)
val DayBackground = Color(0xFFFAFAFC)
val DaySurface = Color(0xFFFFFFFF)

// Night
val NightMapBg = Color(0xFF0C1825)
val NightBackground = Color(0xFF0A1420)
val NightSurface = Color(0xFF11212F)

// Theme.kt
private val DarkColors = darkColorScheme(
    primary = Violet600,
    onPrimary = Color.White,
    secondary = Coral500,
    background = NightBackground,
    surface = NightSurface,
    error = Live,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF7E8D9F)
)

private val LightColors = lightColorScheme(
    primary = Violet600,
    onPrimary = Color.White,
    secondary = Coral500,
    background = DayBackground,
    surface = DaySurface,
    error = Live,
    onSurface = Color(0xFF0C1825),
    onSurfaceVariant = Color(0xFF6B7280)
)

@Composable
fun EventMapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = EventMapTypography, content = content)
}
```

## 6. Темы (расширенная система)

Цветовая палитра — база. Поверх — слои тем, которые меняют фон карты и акценты:

| Слой темы | Когда | Что меняет |
|---|---|---|
| Time-of-day (день/вечер/ночь) | По системному времени | Фон карты, контраст пинов |
| Holiday (Новый Год, Наурыз, Independence) | По календарю Казахстана | Фон-оверлей, декор-частицы (снег, орнамент) |
| City | По геолокации | Силуэт скайлайна, культурный паттерн |
| User-custom | Вручную | Любая комбинация |

Бренд-цвета (`Primary`, `Accent`, `Live`) **остаются неизменными** через все темы — это якорь идентичности.

## 7. Анимации

| Token | Дюрация (ms) | Easing | Применение |
|---|---|---|---|
| `MotionFast` | 150 | EaseOut | Hover, tap feedback |
| `MotionMedium` | 300 | EaseInOut | Раскрытие sheet, переходы |
| `MotionSlow` | 500 | EaseInOut | Сцена карты |
| `PulseDuration` | 2200 | Linear | Пульсация LIVE-объектов |
| `BreatheDuration` | 3000 | EaseInOut | Йога, парки |
| `FlickerDuration` | 1000 | Linear | Огни, индикаторы LIVE |
