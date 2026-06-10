package com.eventmap.map.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmap.map.EventCategory
import com.eventmap.map.EventMap
import com.eventmap.map.EventMapController
import com.eventmap.map.EventOverlay
import com.eventmap.map.EventState
import com.eventmap.map.LatLng
import com.eventmap.map.MapEvent
import com.eventmap.theme.EventMapTheme
import com.eventmap.theme.ui_kit.EventStatus
import com.eventmap.theme.ui_kit.PrimaryGradientButton
import com.eventmap.theme.ui_kit.SecondaryButton
import com.eventmap.theme.ui_kit.StatusPill
import com.eventmap.theme.ui_kit.clickableNoRipple
import org.koin.mp.KoinPlatform.getKoin

private val Astana = LatLng(51.1283, 71.4306)

/**
 * Домашний экран — живая карта (раздел C). Поверх карты: поиск + фильтр,
 * FAB локации, карусель актуальных событий снизу, bottom-sheet события и фильтры.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapContent(
    modifier: Modifier = Modifier,
    onOpenEvent: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onOpenLive: () -> Unit = {},
) {
    val vm = remember { getKoin().get<MapViewModel>() }
    val events by vm.events.collectAsState()
    val filter by vm.filter.collectAsState()
    val selected by vm.selected.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<EventMapController?>(null) }

    // MapContent — не Voyager Screen, поэтому scope ViewModel освобождаем вручную
    DisposableEffect(Unit) { onDispose { vm.onDispose() } }

    // Ошибки экрана (ERRORS.md): errorMessage → снек
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.errorMessage.collect { snackbarHostState.showSnackbar(it) }
    }

    val colors = EventMapTheme.colors

    Box(modifier.background(colors.mapBg)) {
        EventMap(
            initialCenter = Astana,
            initialZoom = 13.0,
            modifier = Modifier.fillMaxSize(),
            onMapReady = { controller = it },
        )
        controller?.let { EventOverlay(controller = it, events = events, onPinClick = { vm.select(it) }) }

        // Верхняя панель: поиск + фильтр
        MapSearchBar(
            activeFilters = filter.categories.size + (if (filter.freeOnly) 1 else 0),
            onFilterClick = { showFilters = true },
            onSearchClick = onSearchClick,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp, start = 16.dp, end = 16.dp),
        )

        // Пилюля «Прямо сейчас» (вход в live-видео ленту)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 104.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(EventMapTheme.colors.live)
                .clickableNoRipple(onOpenLive)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LivePulseDot()
            Spacer(Modifier.width(6.dp))
            Text("Прямо сейчас", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }

        // FAB локации (справа над каруселью)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 180.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoRipple { controller?.animateCamera(Astana, 14.0) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MyLocation,
                contentDescription = "Моя локация",
                tint = EventMapTheme.colors.accent,
                modifier = Modifier.size(22.dp),
            )
        }

        // Карусель событий снизу
        EventCarousel(
            events = events,
            onClick = { vm.select(it) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 200.dp),
        )
    }

    selected?.let { ev ->
        ModalBottomSheet(onDismissRequest = { vm.clearSelection() }, sheetState = rememberModalBottomSheetState()) {
            EventSheetContent(ev, onOpen = {
                vm.clearSelection()
                onOpenEvent(ev.id)
            })
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = rememberModalBottomSheetState()) {
            FiltersSheetContent(filter = filter, vm = vm, onApply = { showFilters = false })
        }
    }
}

@Composable
private fun MapSearchBar(activeFilters: Int, onFilterClick: () -> Unit, onSearchClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoRipple(onSearchClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = EventMapTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Поиск событий и мест", color = EventMapTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (activeFilters > 0) EventMapTheme.colors.accent else MaterialTheme.colorScheme.surface)
                .clickableNoRipple(onFilterClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Tune,
                contentDescription = "Фильтры",
                tint = if (activeFilters > 0) Color.White else EventMapTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// Пульсирующая точка LIVE (масштаб + прозрачность)
@Composable
private fun LivePulseDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
    )
    Box(
        modifier
            .size(8.dp)
            .scale(pulse)
            .alpha(pulse)
            .clip(CircleShape)
            .background(Color.White),
    )
}

@Composable
private fun EventCarousel(events: List<MapEvent>, onClick: (MapEvent) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(events) { ev -> EventCard(ev, onClick = { onClick(ev) }) }
    }
}

@Composable
private fun EventCard(ev: MapEvent, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickableNoRipple(onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(ev.category)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ev.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(ev.subtitle, style = MaterialTheme.typography.bodyMedium, color = EventMapTheme.colors.textSecondary, maxLines = 1)
            }
            StatusPill(ev.state.toStatus())
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${ev.timeLabel} · ${formatKm(ev.distanceKm)}", style = MaterialTheme.typography.bodySmall, color = EventMapTheme.colors.textTertiary)
            Text(ev.priceLabel, style = MaterialTheme.typography.labelLarge, color = EventMapTheme.colors.accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CategoryAvatar(category: EventCategory, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(EventMapTheme.colors.brandGradient)),
        contentAlignment = Alignment.Center,
    ) { Text(category.emoji(), fontSize = (size * 0.45).sp) }
}

@Composable
private fun EventSheetContent(ev: MapEvent, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(ev.category, size = 52)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(ev.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(ev.category.label(), style = MaterialTheme.typography.bodyMedium, color = EventMapTheme.colors.textSecondary)
            }
            StatusPill(ev.state.toStatus())
        }
        Spacer(Modifier.height(16.dp))
        Text(ev.subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        InfoRow("🕒", ev.timeLabel)
        InfoRow("📍", formatKm(ev.distanceKm) + " от вас")
        InfoRow("🎟️", ev.priceLabel)
        if (ev.state == EventState.Live && ev.watching > 0) InfoRow("👀", "${ev.watching} смотрят сейчас")
        Spacer(Modifier.height(20.dp))
        if (ev.state == EventState.Live) {
            PrimaryGradientButton(text = "Я тоже здесь", onClick = {})
        } else {
            PrimaryGradientButton(text = "Пойду", onClick = {})
        }
        Spacer(Modifier.height(10.dp))
        SecondaryButton(text = "Открыть карточку", onClick = onOpen)
    }
}

@Composable
private fun InfoRow(icon: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 15.sp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun FiltersSheetContent(filter: MapFilter, vm: MapViewModel, onApply: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Фильтры", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text("Сбросить", color = EventMapTheme.colors.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickableNoRipple { vm.resetFilter() })
        }
        Spacer(Modifier.height(16.dp))

        SectionTitle("Когда")
        FlowChips(WhenFilter.entries.map { it.label }, selectedLabels = setOf(filter.whenFilter.label)) { label ->
            WhenFilter.entries.firstOrNull { it.label == label }?.let { vm.setWhen(it) }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Категории")
        FlowChips(
            labels = filterableCategories.map { it.label() },
            selectedLabels = filter.categories.map { it.label() }.toSet(),
        ) { label ->
            filterableCategories.firstOrNull { it.label() == label }?.let { vm.toggleCategory(it) }
        }

        Spacer(Modifier.height(16.dp))
        ToggleRow("Только бесплатные", filter.freeOnly) { vm.setFreeOnly(it) }
        ToggleRow("Звук на карте", filter.soundOn) { vm.toggleSound() }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Радиус: ${filter.radiusKm.toInt()} км")
        Slider(value = filter.radiusKm, onValueChange = { vm.setRadius(it) }, valueRange = 1f..20f)

        Spacer(Modifier.height(8.dp))
        SectionTitle("Слои")
        MapLayer.entries.forEach { layer ->
            ToggleRow(layer.label, layer in filter.layers) { vm.toggleLayer(layer) }
        }

        Spacer(Modifier.height(20.dp))
        PrimaryGradientButton(text = "Показать события", onClick = onApply)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Простой перенос чипов по строкам (без зависимости от FlowRow). */
@Composable
private fun FlowChips(labels: List<String>, selectedLabels: Set<String>, onClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.chunked(3).forEach { rowLabels ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowLabels.forEach { label ->
                    val sel = label in selectedLabels
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (sel) EventMapTheme.colors.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, if (sel) EventMapTheme.colors.accent else Color.Transparent, RoundedCornerShape(999.dp))
                            .clickableNoRipple { onClick(label) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (sel) EventMapTheme.colors.accent else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

private fun EventState.toStatus(): EventStatus = when (this) {
    EventState.Live -> EventStatus.Live
    EventState.Soon -> EventStatus.Soon
    EventState.Ending -> EventStatus.Live
}

private fun formatKm(km: Double): String =
    if (km < 1.0) "${(km * 1000).toInt()} м" else "${(km * 10).toInt() / 10.0} км"
