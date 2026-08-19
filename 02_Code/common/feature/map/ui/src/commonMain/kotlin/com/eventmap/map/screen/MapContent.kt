package com.eventmap.map.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
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
import com.eventmap.map.MapStyle
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.EventMapIcons
import com.eventmap.theme.LiveGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.NightTextTertiary
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin

private val Astana = LatLng(51.1283, 71.4306)

// =====================================================================
//  C · Карта — Aurora Violet night palette
//  C1: живая карта, C4: sheet «скоро», C5: sheet «LIVE», C8: фильтры
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapContent(
    modifier: Modifier = Modifier,
    onOpenEvent: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onOpenLive: () -> Unit = {},
    onRecordMoment: (eventId: String, title: String) -> Unit = { _, _ -> },
) {
    val vm = remember { getKoin().get<MapViewModel>() }
    val events by vm.events.collectAsState()
    val filter by vm.filter.collectAsState()
    val selected by vm.selected.collectAsState()
    // режимы C1 (G1–G3, G9): пульс/туман/люди — данные с /map/pulse, /map/fog
    val mode by vm.mode.collectAsState()
    val anomalies by vm.anomalies.collectAsState()
    val fogVisited by vm.fogVisited.collectAsState()
    val friends by vm.friends.collectAsState()
    val pulse by vm.pulse.collectAsState()
    val compass by vm.compass.collectAsState()
    // C2: дефолт — тёмная карта под тему приложения; ☀️ — ручное переключение на день
    var dayStyle by remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()
    var showFilters by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<EventMapController?>(null) }

    DisposableEffect(Unit) { onDispose { vm.onDispose() } }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.errorMessage.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier.background(NightBackground)) {
        // ── Карта ──────────────────────────────────────────────────────
        EventMap(
            initialCenter = Astana,
            initialZoom = 13.0,
            modifier = Modifier.fillMaxSize(),
            onMapReady = { controller = it },
        )
        val shownEvents = when (mode) {
            MapMode.People -> friends.map { it.toPersonPin() }
            MapMode.Heat -> emptyList() // C11: только тепло, без пинов
            else -> events
        }
        controller?.let { EventOverlay(controller = it, events = shownEvents, onPinClick = { vm.select(it) }) }

        // ── C11: тепловая карта — радиальные пятна активности ──
        if (mode == MapMode.Heat) {
            controller?.let { HeatOverlay(controller = it, points = buildHeatPoints(events, pulse, anomalies)) }
        }

        // ── G2: поток людей — частицы стягиваются к событиям с притоком ──
        if (mode == MapMode.Flow) {
            controller?.let { FlowOverlay(controller = it, targets = buildFlowTargets(events, pulse)) }
        }

        // ── Слои режимов (G1–G3): затемнение под туман, баннеры пульса/людей ──
        if (mode == MapMode.Fog) {
            Box(Modifier.fillMaxSize().background(Color(0xFF060B14).copy(alpha = 0.55f)))
        }
        ModeBanner(
            mode = mode,
            anomalies = anomalies,
            fogVisited = fogVisited,
            friendsCount = friends.size,
            // пик тепла — самое «горячее» LIVE-событие (по glow, иначе по зрителям)
            heatPeakLabel = events.maxByOrNull { pulse[it.id]?.glow ?: (it.watching / 1000f) }?.title.orEmpty(),
            // топ притока за 10 мин (crowd10m из /map/pulse)
            flowTopLabel = events
                .mapNotNull { ev -> pulse[ev.id]?.crowd10m?.takeIf { it > 0 }?.let { ev.title to it } }
                .maxByOrNull { it.second }
                ?.let { "+${it.second} за 10 мин · ${it.first}" }
                .orEmpty(),
            onAnomalyClick = { a -> controller?.animateCamera(LatLng(a.lat, a.lng), 15.0) },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 158.dp, start = 16.dp, end = 16.dp),
        )

        // ── Верхняя панель: поиск + чипы фильтров ──────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 52.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MapSearchBar(
                activeFilters = filter.categories.size + (if (filter.freeOnly) 1 else 0),
                onFilterClick = { showFilters = true },
                onSearchClick = onSearchClick,
            )
            FilterChipsRow(filter = filter, vm = vm, onLiveClick = onOpenLive)

            // G6: квест-компас «движ по вкусу» — тап ведёт камеру к событию
            compass?.let { hint ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(NightSurface.copy(alpha = 0.94f))
                        .border(1.dp, Violet400.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                        .clickableNoRipple {
                            controller?.animateCamera(hint.event.location, 15.0)
                            vm.select(hint.event)
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        EventMapIcons.Compass,
                        contentDescription = null,
                        tint = Violet400,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        hint.event.title.take(28) +
                            (hint.distanceM?.let { " · ${formatCompassDist(it)}" } ?: ""),
                        color = Violet400, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // ── C2: день/ночь ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 288.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NightSurface)
                .clickableNoRipple {
                    dayStyle = !dayStyle
                    controller?.setStyle(if (dayStyle) MapStyle.DayLight else MapStyle.NightDark)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (dayStyle) EventMapIcons.Night else EventMapIcons.Day,
                contentDescription = if (dayStyle) "Тёмная карта" else "Светлая карта",
                tint = Color.White,
                modifier = Modifier.size(21.dp),
            )
        }

        // ── FAB локация ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 180.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NightSurface)
                .clickableNoRipple { controller?.animateCamera(Astana, 14.0) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MyLocation,
                contentDescription = "Моя локация",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        // ── Переключатель режимов C1: ✨ Пульс · 🌫️ Туман · 🫂 Люди ──────
        // У самого низа; поднимается над каруселью, только когда есть карточки событий
        val switcherBottom by animateDpAsState(if (events.isEmpty()) 24.dp else 148.dp)
        ModeSwitcher(
            mode = mode,
            onSelect = { vm.setMode(it) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = switcherBottom),
        )

        // ── Карусель событий ─────────────────────────────────────────────
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

    // ── Bottom sheet: C4 (скоро) / C5 (LIVE) ───────────────────────────
    selected?.let { ev ->
        ModalBottomSheet(
            onDismissRequest = { vm.clearSelection() },
            sheetState = rememberModalBottomSheetState(),
            containerColor = NightSurface,
            dragHandle = { SheetHandle() },
        ) {
            if (ev.category == EventCategory.Person) {
                // C10: друг на карте → «Позвать с собой»
                FriendSheet(
                    ev = ev,
                    onWave = {
                        val friendId = ev.id.removePrefix("friend-")
                        vm.clearSelection()
                        vm.waveToFriend(friendId) { ok ->
                            uiScope.launch {
                                snackbarHostState.showSnackbar(
                                    if (ok) "🫂 Позвали! Другу прилетит уведомление" else "Не получилось — попробуй позже",
                                )
                            }
                        }
                    },
                )
            } else {
                when (ev.state) {
                    EventState.Live, EventState.Ending ->
                        LiveSheet(
                            ev,
                            participants = vm.liveParticipants.collectAsState().value,
                            onOpenReel = { vm.clearSelection(); onOpenEvent(ev.id) },
                            // C5: «Я тоже здесь · снять момент» — рилс с привязкой к событию
                            onRecordMoment = { vm.clearSelection(); onRecordMoment(ev.id, ev.title) },
                        )
                    EventState.Soon ->
                        SoonSheet(ev, onOpen = { vm.clearSelection(); onOpenEvent(ev.id) })
                }
            }
        }
    }

    // ── Bottom sheet: C8 Фильтры ────────────────────────────────────────
    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = NightSurface,
            dragHandle = { SheetHandle() },
        ) {
            FiltersSheetContent(filter = filter, vm = vm, onApply = { showFilters = false })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  C1 — Поиск-пилюля + кнопка фильтров
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MapSearchBar(activeFilters: Int, onFilterClick: () -> Unit, onSearchClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(NightSurface)
                .clickableNoRipple(onSearchClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = NightTextSecondary,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Поиск событий и мест", color = NightTextSecondary, fontSize = 14.sp)
        }
        val hasFilters = activeFilters > 0
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (hasFilters) Modifier.background(Brush.linearGradient(BrandGradient))
                    else Modifier.background(NightSurface),
                )
                .clickableNoRipple(onFilterClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Tune,
                contentDescription = "Фильтры",
                tint = if (hasFilters) Color.White else NightTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  C1 — Ряд чипов фильтров: Сегодня · LIVE · категории
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FilterChipsRow(filter: MapFilter, vm: MapViewModel, onLiveClick: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            val sel = filter.whenFilter == WhenFilter.Today
            FilterChip(label = "Сегодня", emoji = "📅", selected = sel) {
                vm.setWhen(if (sel) WhenFilter.Now else WhenFilter.Today)
            }
        }
        item {
            // LIVE chip → открывает ленту live-видео
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.linearGradient(LiveGradient))
                    .clickableNoRipple(onLiveClick)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LivePulseDot()
                Spacer(Modifier.width(5.dp))
                Text(
                    "LIVE",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
        items(filterableCategories) { cat ->
            val sel = cat in filter.categories
            FilterChip(label = cat.label(), emoji = cat.emoji(), selected = sel) { vm.toggleCategory(cat) }
        }
    }
}

@Composable
private fun FilterChip(label: String, emoji: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Violet600.copy(alpha = 0.28f) else NightSurface.copy(alpha = 0.92f))
            .border(
                1.dp,
                if (selected) Violet400.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(999.dp),
            )
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            "$emoji $label",
            color = if (selected) Violet400 else NightTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  C11 · Тепловая карта: радиальные пятна активности поверх карты
// ─────────────────────────────────────────────────────────────────────────────

private data class HeatPoint(val location: LatLng, val intensity: Float)

// Источники тепла: LIVE-события (glow из /map/pulse, фолбэк — зрители) + аномалии G9
private fun buildHeatPoints(
    events: List<MapEvent>,
    pulse: Map<String, com.eventmap.map.data.PulseEventNet>,
    anomalies: List<com.eventmap.map.data.AnomalyNet>,
): List<HeatPoint> {
    val eventHeat = events.mapNotNull { ev ->
        val glow = pulse[ev.id]?.glow
        val intensity = when {
            glow != null -> glow
            ev.state == EventState.Live -> (0.3f + ev.watching / 500f).coerceAtMost(1f)
            else -> 0.15f
        }
        if (intensity <= 0.05f) null else HeatPoint(ev.location, intensity.coerceIn(0f, 1f))
    }
    val anomalyHeat = anomalies.map { a ->
        HeatPoint(LatLng(a.lat, a.lng), (0.25f + a.userCount / 50f).coerceIn(0f, 1f))
    }
    return eventHeat + anomalyHeat
}

// Пятна: цвет от фиолетового (тихо) к красному (пик), радиус и альфа растут с интенсивностью.
// Как EventOverlay: подписка на viewport — пятна следуют за панорамой/зумом.
@Composable
private fun HeatOverlay(controller: EventMapController, points: List<HeatPoint>) {
    val viewport by controller.viewport.collectAsState()

    Canvas(Modifier.fillMaxSize()) {
        // viewport читается выше — Compose перерисует Canvas при движении камеры
        @Suppress("UNUSED_EXPRESSION") viewport
        points.forEach { point ->
            val center = controller.project(point.location) ?: return@forEach
            val radius = (70 + 90 * point.intensity).dp.toPx()
            val hot = lerp(Violet600, Color(0xFFFF4757), point.intensity)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        hot.copy(alpha = 0.10f + 0.35f * point.intensity),
                        hot.copy(alpha = 0.04f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  G2 · Поток людей: частицы, стягивающиеся к событиям с притоком
// ─────────────────────────────────────────────────────────────────────────────

private data class FlowTarget(val location: LatLng, val strength: Float)

// События с притоком за 10 мин (crowd10m); демо-фолбэк — LIVE со зрителями
private fun buildFlowTargets(
    events: List<MapEvent>,
    pulse: Map<String, com.eventmap.map.data.PulseEventNet>,
): List<FlowTarget> {
    val withCrowd = events.mapNotNull { ev ->
        val crowd = pulse[ev.id]?.crowd10m ?: 0
        if (crowd > 0) FlowTarget(ev.location, (0.3f + crowd / 40f).coerceAtMost(1f)) else null
    }
    if (withCrowd.isNotEmpty()) return withCrowd
    return events.filter { it.state == EventState.Live }
        .map { FlowTarget(it.location, (0.3f + it.watching / 500f).coerceAtMost(1f)) }
}

// 6 частиц на цель: движутся по радиусу к центру, фаза сдвинута — непрерывный поток
@Composable
private fun FlowOverlay(controller: EventMapController, targets: List<FlowTarget>) {
    val viewport by controller.viewport.collectAsState()
    val transition = rememberInfiniteTransition()
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = androidx.compose.animation.core.LinearEasing)),
    )

    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION") viewport
        val flowColor = Color(0xFF4FC3F7)
        targets.forEach { target ->
            val center = controller.project(target.location) ?: return@forEach
            val maxR = (60 + 60 * target.strength).dp.toPx()
            repeat(6) { i ->
                val phase = (t + i / 6f) % 1f          // 0 → 1: от края к центру
                val r = maxR * (1f - phase)
                val angle = i * (2f * kotlin.math.PI.toFloat() / 6f) + target.strength // фикс. лучи
                val x = center.x + r * kotlin.math.cos(angle)
                val y = center.y + r * kotlin.math.sin(angle)
                drawCircle(
                    color = flowColor.copy(alpha = 0.15f + 0.5f * phase), // ярче у цели
                    radius = (2 + 2 * target.strength).dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(x, y),
                )
            }
        }
    }
}

// «320 м» / «1.2 км» для компаса G6
private fun formatCompassDist(m: Int): String =
    if (m >= 1000) "${m / 1000}.${(m % 1000) / 100} км" else "$m м"

// ─────────────────────────────────────────────────────────────────────────────
//  C10 · Друзья на карте: пин + шторка «Позвать с собой»
// ─────────────────────────────────────────────────────────────────────────────

// FriendLocationNet → пин карты (категория Person рисуется как аватар-кружок)
private fun com.eventmap.map.data.FriendLocationNet.toPersonPin(): MapEvent = MapEvent(
    id = "friend-$userId",
    location = LatLng(lat, lng),
    category = EventCategory.Person,
    state = EventState.Soon,
    title = name.ifEmpty { "Друг" },
    subtitle = "делится геолокацией",
    timeLabel = agoLabel,
    priceLabel = "",
    isFree = true,
    distanceKm = 0.0,
)

@Composable
private fun FriendSheet(ev: MapEvent, onWave: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)),
                contentAlignment = Alignment.Center,
            ) { Text("🙂", fontSize = 24.sp) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(ev.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(
                    "🫂 делится геолокацией" + if (ev.timeLabel.isNotEmpty()) " · ${ev.timeLabel}" else "",
                    fontSize = 12.sp, color = NightTextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(BrandGradient))
                .clickableNoRipple(onWave),
            contentAlignment = Alignment.Center,
        ) {
            Text("🫂 Позвать с собой", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  C1 · Режимы карты: переключатель + баннеры (G1 пульс · G3 туман · G9 аномалии)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModeSwitcher(mode: MapMode, onSelect: (MapMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(NightSurface.copy(alpha = 0.94f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(MapMode.Pulse, MapMode.Flow, MapMode.Heat, MapMode.Fog, MapMode.People).forEach { m ->
            val selectedMode = mode == m
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .then(
                        if (selectedMode) Modifier.background(Brush.linearGradient(BrandGradient))
                        else Modifier,
                    )
                    .clickableNoRipple { onSelect(m) }
                    .padding(horizontal = if (selectedMode) 14.dp else 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = m.icon(),
                    contentDescription = m.label,
                    tint = if (selectedMode) Color.White else NightTextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                if (selectedMode) {
                    Text(
                        m.label,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

private fun MapMode.icon(): ImageVector = when (this) {
    MapMode.Pulse -> EventMapIcons.Pulse
    MapMode.Flow -> EventMapIcons.Flow
    MapMode.Heat -> EventMapIcons.Heat
    MapMode.Fog -> EventMapIcons.Fog
    MapMode.People -> EventMapIcons.People
    MapMode.None -> EventMapIcons.Map
}

@Composable
private fun ModeBanner(
    mode: MapMode,
    anomalies: List<com.eventmap.map.data.AnomalyNet>,
    fogVisited: Int?,
    friendsCount: Int,
    heatPeakLabel: String,
    flowTopLabel: String,
    onAnomalyClick: (com.eventmap.map.data.AnomalyNet) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        MapMode.Fog -> Column(
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(NightSurface.copy(alpha = 0.95f))
                .border(1.dp, Violet400.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                if (fogVisited != null) "🌫️ Туман войны · открыто районов: $fogVisited"
                else "🌫️ Туман войны · считаем открытые районы…",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                "Открой новый район — получи XP и значок",
                color = NightTextSecondary, fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        MapMode.Flow -> Column(
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(NightSurface.copy(alpha = 0.95f))
                .border(1.dp, Color(0xFF4FC3F7).copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("🌊 Поток людей — стягиваются к событиям", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                if (flowTopLabel.isNotEmpty()) flowTopLabel else "Пока тихо — приток появится по мере пингов",
                color = NightTextSecondary, fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        MapMode.Heat -> Column(
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(NightSurface.copy(alpha = 0.95f))
                .border(1.dp, Color(0xFFFF8A50).copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("🔥 Тепловая карта · активность прямо сейчас", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("тихо", color = NightTextSecondary, fontSize = 10.sp)
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Violet600.copy(alpha = 0.5f), Color(0xFFFF8A50), Color(0xFFFF4757)),
                            ),
                        ),
                )
                Spacer(Modifier.width(6.dp))
                Text("оживлённо", color = NightTextSecondary, fontSize = 10.sp)
            }
            if (heatPeakLabel.isNotEmpty()) {
                Text("🔥 пик · $heatPeakLabel", color = Color(0xFFFF8A50), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        MapMode.People -> Column(
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(NightSurface.copy(alpha = 0.95f))
                .border(1.dp, Violet400.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                if (friendsCount > 0) "🫂 Друзей делятся геолокацией: $friendsCount"
                else "🫂 Пока никто из друзей не делится гео",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                if (friendsCount > 0) "Тапни аватар — «Позвать с собой»"
                else "Включи Sharing в настройках — и тебя увидят друзья",
                color = NightTextSecondary, fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        MapMode.Pulse -> {
            // G9: «⚡ Здесь что-то происходит» — сгусток людей без события
            val a = anomalies.firstOrNull() ?: return
            Column(
                modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(NightSurface.copy(alpha = 0.95f))
                    .border(1.dp, Color(0xFFFFB84D).copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .clickableNoRipple { onAnomalyClick(a) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("⚡ Здесь что-то происходит", color = Color(0xFFFFB84D), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${a.userCount} человек собрались рядом · без события. Тапни — посмотрим",
                    color = NightTextSecondary, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        MapMode.None -> Unit
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Анимированная пульс-точка LIVE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LivePulseDot() {
    val transition = rememberInfiniteTransition()
    val pulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
    )
    Box(
        Modifier
            .size(7.dp)
            .scale(pulse)
            .alpha(pulse)
            .clip(CircleShape)
            .background(Color.White),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Карусель событий
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EventCarousel(events: List<MapEvent>, onClick: (MapEvent) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(events) { ev -> EventCard(ev, onClick = { onClick(ev) }) }
    }
}

@Composable
private fun EventCard(ev: MapEvent, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(258.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NightSurface)
            .clickableNoRipple(onClick)
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(ev.category, size = 40)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ev.title, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(ev.subtitle, fontSize = 12.sp, color = NightTextSecondary, maxLines = 1)
            }
            Spacer(Modifier.width(6.dp))
            when (ev.state) {
                EventState.Live, EventState.Ending -> LiveBadge()
                EventState.Soon -> SoonBadge()
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${ev.timeLabel} · ${formatKm(ev.distanceKm)}", fontSize = 11.sp, color = NightTextTertiary)
            Text(ev.priceLabel, fontSize = 12.sp, color = Violet400, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LiveBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(LiveGradient))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LivePulseDot()
        Spacer(Modifier.width(4.dp))
        Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun SoonBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Violet600.copy(alpha = 0.22f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text("скоро", color = Violet400, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryAvatar(category: EventCategory, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(BrandGradient)),
        contentAlignment = Alignment.Center,
    ) {
        Text(category.emoji(), fontSize = (size * 0.45).sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Sheet handle (grip)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.15f)),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  C4 — Bottom-sheet «Скоро»
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SoonSheet(ev: MapEvent, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(ev.category, size = 50)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ev.title, fontSize = 19.sp, color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                Text(ev.category.label(), fontSize = 12.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        // Мета-чипы: место / время / цена
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaChip("📍", formatKm(ev.distanceKm))
            MetaChip("🕒", ev.timeLabel)
            MetaChip("🎟️", ev.priceLabel)
        }

        if (ev.subtitle.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(ev.subtitle, fontSize = 13.sp, color = NightTextSecondary, lineHeight = 18.sp)
        }

        Spacer(Modifier.height(20.dp))

        // Primary CTA
        val ctaLabel = if (ev.isFree) "Приду" else "Купить"
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(BrandGradient))
                .clickableNoRipple(onOpen),
            contentAlignment = Alignment.Center,
        ) {
            Text(ctaLabel, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }

        Spacer(Modifier.height(8.dp))

        // Ghost: открыть рилс
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .clickableNoRipple(onOpen),
            contentAlignment = Alignment.Center,
        ) {
            Text("Открыть событие", color = Color.White.copy(alpha = 0.70f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MetaChip(emoji: String, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        Text(label, color = NightTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  C5 — Bottom-sheet «LIVE»
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LiveSheet(ev: MapEvent, participants: List<String>, onOpenReel: () -> Unit, onRecordMoment: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
    ) {
        // LIVE badge + счётчик
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.linearGradient(LiveGradient))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LivePulseDot()
                Spacer(Modifier.width(5.dp))
                Text(
                    "LIVE",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                )
            }
            if (ev.watching > 0) {
                Text("👀 ${ev.watching} сейчас", color = NightTextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(ev.title, fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 2)
        Text(ev.category.label(), fontSize = 12.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))

        Spacer(Modifier.height(14.dp))

        // Лента live-кружков участников (C6) — реальные имена с бэка
        LiveCirclesRow(participants)

        Spacer(Modifier.height(20.dp))

        // ▶ Открыть рилс события
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(BrandGradient))
                .clickableNoRipple(onOpenReel),
            contentAlignment = Alignment.Center,
        ) {
            Text("▶  Открыть рилс события", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }

        Spacer(Modifier.height(8.dp))

        // 📍 Я тоже здесь · снять момент (C5) — рилс уйдёт с привязкой к этому событию
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .clickableNoRipple(onRecordMoment),
            contentAlignment = Alignment.Center,
        ) {
            Text("📍  Я тоже здесь · снять момент", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// Кружки участников: реальные имена (инициал в рамке LIVE-градиента), «+N» при переполнении.
// Пусто — честная подпись вместо фейковых эмодзи.
@Composable
private fun LiveCirclesRow(participants: List<String>) {
    if (participants.isEmpty()) {
        Text(
            "Пока никто не отметился — будь первым 📸",
            color = NightTextSecondary,
            fontSize = 12.sp,
        )
        return
    }
    val shown = participants.take(6)
    val overflow = participants.size - shown.size
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(shown) { name ->
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(2.dp, Brush.linearGradient(LiveGradient), RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.trim().take(1).uppercase().ifBlank { "•" },
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        if (overflow > 0) {
            item {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.07f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+$overflow", color = NightTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  C8 — Фильтры
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FiltersSheetContent(filter: MapFilter, vm: MapViewModel, onApply: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Фильтры", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text(
                "Сбросить",
                color = Violet400,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickableNoRipple { vm.resetFilter() },
            )
        }

        Spacer(Modifier.height(18.dp))
        SheetSectionLabel("КОГДА")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WhenFilter.entries.forEach { wf ->
                val sel = filter.whenFilter == wf
                DarkChip(label = wf.label, selected = sel) { vm.setWhen(wf) }
            }
        }

        Spacer(Modifier.height(18.dp))
        SheetSectionLabel("КАТЕГОРИИ")
        filterableCategories.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cat ->
                    val sel = cat in filter.categories
                    DarkChip(label = "${cat.emoji()} ${cat.label()}", selected = sel) { vm.toggleCategory(cat) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))
        SheetSectionLabel("РАССТОЯНИЕ  ${filter.radiusKm.toInt()} КМ")
        Slider(
            value = filter.radiusKm,
            onValueChange = { vm.setRadius(it) },
            valueRange = 1f..20f,
            colors = SliderDefaults.colors(thumbColor = Violet400, activeTrackColor = Violet600),
        )

        Spacer(Modifier.height(12.dp))
        SheetSectionLabel("СЛОИ")
        MapLayer.entries.forEach { layer ->
            ToggleRow(layer.label, layer in filter.layers) { vm.toggleLayer(layer) }
        }

        Spacer(Modifier.height(8.dp))
        ToggleRow("Только бесплатные", filter.freeOnly) { vm.setFreeOnly(it) }
        ToggleRow("Звук на карте", filter.soundOn) { vm.toggleSound() }

        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(BrandGradient))
                .clickableNoRipple(onApply),
            contentAlignment = Alignment.Center,
        ) {
            Text("Показать события", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text,
        color = NightTextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun DarkChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Violet600.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.07f))
            .border(
                1.dp,
                if (selected) Violet400.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(999.dp),
            )
            .clickableNoRipple(onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) Violet400 else NightTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Violet600,
                uncheckedThumbColor = NightTextTertiary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
            ),
        )
    }
    Spacer(Modifier.height(8.dp))
}

private fun formatKm(km: Double): String =
    if (km < 1.0) "${(km * 1000).toInt()} м" else "${(km * 10).toInt() / 10.0} км"
