package com.eventmap.map.screen

import com.eventmap.map.EventCategory
import com.eventmap.map.EventState
import com.eventmap.map.LatLng
import com.eventmap.map.MapEvent

enum class WhenFilter(val label: String) {
    Now("Сейчас"), Today("Сегодня"), Tomorrow("Завтра"), Week("Неделя")
}

enum class MapLayer(val label: String) {
    Live("События live"),
    Soon("События скоро"),
    Video("Кружки видео"),
    People("Люди"),
    Heatmap("Тепловая карта"),
}

data class MapFilter(
    val whenFilter: WhenFilter = WhenFilter.Today,
    val categories: Set<EventCategory> = emptySet(), // пусто = все
    val freeOnly: Boolean = false,
    val radiusKm: Float = 5f,
    val soundOn: Boolean = true,
    val layers: Set<MapLayer> = setOf(MapLayer.Live, MapLayer.Soon, MapLayer.Video),
)

// Человекочитаемые подписи категорий + эмодзи (для карточек/фильтра).
fun EventCategory.label(): String = when (this) {
    EventCategory.Stadium -> "Спорт"
    EventCategory.Concert -> "Концерт"
    EventCategory.Yoga -> "Йога"
    EventCategory.FoodDiscount -> "Еда"
    EventCategory.ShopDiscount -> "Скидки"
    EventCategory.KidsEvent -> "Детям"
    EventCategory.OpenLesson -> "Лекция"
    EventCategory.Quiz -> "Квиз"
    EventCategory.BookClub -> "Книжклуб"
    EventCategory.Person -> "Люди"
    EventCategory.LiveVideo -> "Видео"
}

fun EventCategory.emoji(): String = when (this) {
    EventCategory.Stadium -> "⚽"
    EventCategory.Concert -> "🎵"
    EventCategory.Yoga -> "🧘"
    EventCategory.FoodDiscount -> "🍔"
    EventCategory.ShopDiscount -> "🛍️"
    EventCategory.KidsEvent -> "🧸"
    EventCategory.OpenLesson -> "📚"
    EventCategory.Quiz -> "🧩"
    EventCategory.BookClub -> "📖"
    EventCategory.Person -> "🧑"
    EventCategory.LiveVideo -> "🎬"
}

// Категории, доступные в фильтре (без служебных Person/LiveVideo).
val filterableCategories = listOf(
    EventCategory.Stadium, EventCategory.Concert, EventCategory.Yoga,
    EventCategory.FoodDiscount, EventCategory.KidsEvent, EventCategory.OpenLesson,
    EventCategory.Quiz, EventCategory.BookClub,
)

// Демо-данные вокруг Астаны (пока бэкенд/Mapbox не подключены).
fun sampleEvents(): List<MapEvent> = listOf(
    MapEvent(
        id = "1", location = LatLng(51.1283, 71.4306), category = EventCategory.Concert,
        state = EventState.Live, title = "Концерт у Байтерека",
        subtitle = "Indie-вечер под открытым небом", timeLabel = "Идёт · до 23:00",
        priceLabel = "от 3 000 ₸", isFree = false, distanceKm = 1.2, watching = 128,
    ),
    MapEvent(
        id = "2", location = LatLng(51.1083, 71.4156), category = EventCategory.Stadium,
        state = EventState.Live, title = "Кайрат — Астана",
        subtitle = "Премьер-лига, 2-й тайм", timeLabel = "Идёт · 67'",
        priceLabel = "от 2 000 ₸", isFree = false, distanceKm = 3.4, watching = 540,
    ),
    MapEvent(
        id = "3", location = LatLng(51.1380, 71.4100), category = EventCategory.Yoga,
        state = EventState.Soon, title = "Йога в парке Первого Президента",
        subtitle = "Утренняя практика", timeLabel = "Завтра · 08:00",
        priceLabel = "Бесплатно", isFree = true, distanceKm = 5.1,
    ),
    MapEvent(
        id = "4", location = LatLng(51.1326, 71.3960), category = EventCategory.FoodDiscount,
        state = EventState.Soon, title = "−50% в Coffee BOOM",
        subtitle = "Счастливые часы", timeLabel = "Сегодня · 18:00–20:00",
        priceLabel = "Скидка", isFree = true, distanceKm = 0.8,
    ),
    MapEvent(
        id = "5", location = LatLng(51.1450, 71.4300), category = EventCategory.Quiz,
        state = EventState.Soon, title = "Квиз «Мозгобойня»",
        subtitle = "Командная игра в баре", timeLabel = "Сегодня · 19:30",
        priceLabel = "от 2 500 ₸", isFree = false, distanceKm = 2.0,
    ),
    MapEvent(
        id = "6", location = LatLng(51.0905, 71.4030), category = EventCategory.OpenLesson,
        state = EventState.Soon, title = "Открытая лекция: AI и город",
        subtitle = "Лекторий", timeLabel = "Сегодня · 20:00",
        priceLabel = "Бесплатно", isFree = true, distanceKm = 1.6,
    ),
)
