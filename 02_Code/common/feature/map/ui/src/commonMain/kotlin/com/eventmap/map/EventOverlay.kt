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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.eventmap.theme.ui_kit.clickableNoRipple
import kotlin.math.roundToInt

// Ниже этого зума пины группируются в кластеры (30+ событий на зуме города — каша)
private const val CLUSTER_MAX_ZOOM = 12.8

/**
 * Compose-overlay поверх карты: для каждого события рисует маркер на спроецированной
 * позиции (controller.project). У LIVE-событий — пульсирующее кольцо.
 * На малом зуме соседние пины схлопываются в кластер с числом; тап — подлёт камеры.
 */
@Composable
fun EventOverlay(
    controller: EventMapController,
    events: List<MapEvent>,
    modifier: Modifier = Modifier,
    onPinClick: (MapEvent) -> Unit = {},
) {
    // ВАЖНО: viewport надо реально ПРОЧИТАТЬ, иначе Compose не подпишется на его изменения
    // и overlay не перерисуется при панораме/зуме — пины «поедут» вместе с картой.
    val viewport by controller.viewport.collectAsState()
    val cellPx = with(LocalDensity.current) { 76.dp.toPx() }

    Box(modifier.fillMaxSize()) {
        if (viewport.zoom >= CLUSTER_MAX_ZOOM) {
            events.forEach { event ->
                // viewport в ключе remember → проекция пересчитывается на каждое движение камеры,
                // поэтому маркер остаётся привязанным к своим гео-координатам.
                val screen = remember(event.location, viewport) { controller.project(event.location) }
                    ?: return@forEach
                EventMarker(event = event, position = screen, onClick = { onPinClick(event) })
            }
        } else {
            // Кластеризация по экранной сетке: соседние по пикселям пины → одна группа
            val groups = remember(events, viewport) {
                events.mapNotNull { e -> controller.project(e.location)?.let { p -> e to p } }
                    .groupBy { (_, p) -> (p.x / cellPx).toInt() to (p.y / cellPx).toInt() }
                    .values.toList()
            }
            groups.forEach { group ->
                if (group.size == 1) {
                    val (event, screen) = group.first()
                    EventMarker(event = event, position = screen, onClick = { onPinClick(event) })
                } else {
                    val cx = group.map { it.second.x }.average().toFloat()
                    val cy = group.map { it.second.y }.average().toFloat()
                    val hasLive = group.any { it.first.state == EventState.Live }
                    val targetLat = group.map { it.first.location.lat }.average()
                    val targetLon = group.map { it.first.location.lon }.average()
                    val targetZoom = viewport.zoom + 2.2
                    ClusterMarker(
                        count = group.size,
                        position = Offset(cx, cy),
                        hasLive = hasLive,
                        onClick = {
                            controller.animateCamera(LatLng(targetLat, targetLon), zoom = targetZoom)
                        },
                    )
                }
            }
        }
    }
}

// Кластер: фиолетовый круг с числом; красная точка — внутри есть LIVE
@Composable
private fun ClusterMarker(count: Int, position: Offset, hasLive: Boolean, onClick: () -> Unit) {
    val sizeDp = 46
    Box(
        modifier = Modifier.offset {
            IntOffset(
                (position.x - (sizeDp / 2).dp.toPx()).roundToInt(),
                (position.y - (sizeDp / 2).dp.toPx()).roundToInt(),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(sizeDp.dp).clip(CircleShape)
                .background(Color(0xFF7C5CFF))
                .border(2.dp, Color.White, CircleShape)
                .clickableNoRipple(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text("$count", fontSize = 15.sp, color = Color.White)
        }
        if (hasLive) {
            Box(
                Modifier.offset { IntOffset((sizeDp / 2 - 5).dp.toPx().roundToInt(), (-(sizeDp / 2) + 5).dp.toPx().roundToInt()) }
                    .size(12.dp).clip(CircleShape)
                    .background(Color(0xFFFF4757)).border(1.dp, Color.White, CircleShape),
            )
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
            Icon(
                imageVector = iconFor(event),
                contentDescription = event.category.name,
                tint = Color(0xFF17141E),
                modifier = Modifier.size(21.dp),
            )
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

private fun iconFor(event: MapEvent): ImageVector {
    if (event.kind == "live_signal") return Icons.Rounded.Bolt
    return when (event.category) {
        EventCategory.Stadium -> Icons.Rounded.SportsSoccer
        EventCategory.Concert -> Icons.Rounded.MusicNote
        EventCategory.Yoga -> Icons.Rounded.SelfImprovement
        EventCategory.FoodDiscount -> Icons.Rounded.Restaurant
        EventCategory.ShopDiscount -> Icons.Rounded.LocalMall
        EventCategory.KidsEvent -> Icons.Rounded.ChildCare
        EventCategory.OpenLesson -> Icons.Rounded.School
        EventCategory.Quiz -> Icons.Rounded.Quiz
        EventCategory.BookClub -> Icons.Rounded.MenuBook
        EventCategory.Person -> Icons.Rounded.Person
        EventCategory.LiveVideo -> Icons.Rounded.Videocam
    }
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
    val kind: String = "planned",
    val confirmationCount: Int = 0,
    val interestCount: Int = 0,
)

enum class EventCategory {
    Stadium, Concert, Yoga, FoodDiscount, ShopDiscount,
    KidsEvent, OpenLesson, Quiz, BookClub, Person, LiveVideo
}

enum class EventState { Soon, Live, Ending }

/** Тип анимации пина на карте (Этап 1 — встроенные бесплатные эффекты). */
enum class PinAnimation { None, Pulse, Wave, Glow, Blink }
