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
    val transition = rememberInfiniteTransition()

    // мигание самого маркера (для Blink)
    val blink by transition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
    )
    val markerAlpha = if (event.animation == PinAnimation.Blink) blink else 1f

    Box(
        modifier = Modifier.offset {
            IntOffset(
                (position.x - (markerDp / 2).dp.toPx()).roundToInt(),
                (position.y - (markerDp / 2).dp.toPx()).roundToInt(),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        // фоновый анимированный эффект по выбранному типу анимации пина
        when (event.animation) {
            PinAnimation.Pulse, PinAnimation.Wave -> {
                PulseRing(transition, ring, markerDp, durationMs = 2200)
                if (event.animation == PinAnimation.Wave) {
                    PulseRing(transition, ring, markerDp, durationMs = 1500)
                }
            }
            PinAnimation.Glow -> {
                val g by transition.animateFloat(
                    initialValue = 1.2f, targetValue = 1.9f,
                    animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
                )
                val a by transition.animateFloat(
                    initialValue = 0.35f, targetValue = 0.08f,
                    animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
                )
                Box(Modifier.size(markerDp.dp).scale(g).alpha(a).clip(CircleShape).background(ring))
            }
            PinAnimation.None, PinAnimation.Blink -> Unit
        }

        Box(
            Modifier.size(markerDp.dp).alpha(markerAlpha).clip(CircleShape)
                .background(ring).border(2.dp, Color.White, CircleShape)
                .clickableNoRipple(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(emojiFor(event.category), fontSize = 18.sp)
        }
    }
}

@Composable
private fun PulseRing(
    transition: androidx.compose.animation.core.InfiniteTransition,
    color: Color,
    markerDp: Int,
    durationMs: Int,
) {
    val pulse by transition.animateFloat(
        initialValue = 1f, targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(durationMs), RepeatMode.Restart),
    )
    val a by transition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(durationMs), RepeatMode.Restart),
    )
    Box(Modifier.size(markerDp.dp).scale(pulse).alpha(a).clip(CircleShape).background(color))
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
    val animation: PinAnimation = PinAnimation.Pulse,
)

enum class EventCategory {
    Stadium, Concert, Yoga, FoodDiscount, ShopDiscount,
    KidsEvent, OpenLesson, Quiz, BookClub, Person, LiveVideo
}

enum class EventState { Soon, Live, Ending }

/** Тип анимации пина на карте (Этап 1 — встроенные бесплатные эффекты). */
enum class PinAnimation { None, Pulse, Wave, Glow, Blink }
