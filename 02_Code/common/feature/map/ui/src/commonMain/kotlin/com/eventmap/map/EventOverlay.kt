package com.eventmap.map

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.eventmap.theme.ui_kit.clickableNoRipple
import kotlin.math.roundToInt

/**
 * Compose-overlay поверх карты: для каждого события рисует маркер на спроецированной
 * позиции (controller.project). У LIVE-событий — пульсирующее кольцо.
 */
@Composable
fun EventOverlay(
    controller: EventMapController,
    events: List<MapEvent>,
    modifier: Modifier = Modifier,
    onPinClick: (MapEvent) -> Unit = {},
) {
    // подписка на viewport заставляет overlay перерисовываться при панораме/зуме
    val viewport by controller.viewport.collectAsState()

    Box(modifier.fillMaxSize()) {
        events.forEach { event ->
            val screen = controller.project(event.location) ?: return@forEach
            EventMarker(event = event, position = screen, onClick = { onPinClick(event) })
        }
    }
}

@Composable
private fun EventMarker(event: MapEvent, position: Offset, onClick: () -> Unit) {
    val markerDp = 40
    val ring = statusColor(event.state)

    Box(
        modifier = Modifier.offset {
            IntOffset(
                (position.x - (markerDp / 2).dp.toPx()).roundToInt(),
                (position.y - (markerDp / 2).dp.toPx()).roundToInt(),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        if (event.state == EventState.Live) {
            val transition = rememberInfiniteTransition()
            val pulse by transition.animateFloat(
                initialValue = 1f, targetValue = 2.1f,
                animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Restart),
            )
            val ringAlpha by transition.animateFloat(
                initialValue = 0.5f, targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Restart),
            )
            Box(
                Modifier.size(markerDp.dp).scale(pulse).alpha(ringAlpha)
                    .clip(CircleShape).background(ring),
            )
        }

        Box(
            Modifier.size(markerDp.dp).clip(CircleShape)
                .background(ring).border(2.dp, Color.White, CircleShape)
                .clickableNoRipple(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(emojiFor(event.category), fontSize = 18.sp)
        }
    }
}

private fun statusColor(state: EventState): Color = when (state) {
    EventState.Live -> Color(0xFFFF4757)
    EventState.Soon -> Color(0xFFFAC775)
    EventState.Ending -> Color(0xFFFF4757)
}

private fun emojiFor(category: EventCategory): String = when (category) {
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

/**
 * Минимальная модель события для overlay'я.
 */
data class MapEvent(
    val id: String,
    val location: LatLng,
    val category: EventCategory,
    val state: EventState,  // soon, live, ending
    val title: String,
    // доп. поля для карточек/bottom-sheet (UI-слой)
    val subtitle: String = "",
    val timeLabel: String = "",
    val priceLabel: String = "",
    val distanceKm: Double = 0.0,
    val isFree: Boolean = true,
    val watching: Int = 0,
)

enum class EventCategory {
    Stadium, Concert, Yoga, FoodDiscount, ShopDiscount,
    KidsEvent, OpenLesson, Quiz, BookClub, Person, LiveVideo
}

enum class EventState { Soon, Live, Ending }
