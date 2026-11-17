package com.eventmap.map.city

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eventmap.core.network.CoreConfig
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.ktor.createMediaHttpClient
import com.eventmap.event.EventRepository
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.EventVenue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import org.koin.mp.KoinPlatform.getKoin
import androidx.compose.ui.platform.LocalUriHandler
import eventmap.common.feature.map.ui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.math.*

/** The main map: the live 3D city. The provider (2D) map stays one tap away until it has parity. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun GameCityContent(
    onExit: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onCreateHere: (CityCreationPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = remember { getKoin().get<EventRepository>() }
    val manifest = remember { getKoin().get<CoreConfig>().baseUrl.trimEnd('/') + "/world/manifest.json" }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    var events by remember { mutableStateOf<List<EventDetail>>(emptyList()) }
    val coverClient = remember { createMediaHttpClient(allowRedirects = false) }
    val coverLoader = remember { CityCovers(coverClient, repo::getMedia) }
    DisposableEffect(coverClient) { onDispose { coverClient.close() } }
    var coverIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var covers by remember { mutableStateOf<List<CityCover>>(emptyList()) }
    var eventsState by remember { mutableStateOf("Загружаем события…") }
    var fetchRevision by remember { mutableIntStateOf(0) }
    var focusRevision by remember { mutableIntStateOf(0) }
    var updateRevision by remember { mutableIntStateOf(0) }
    // Whole-city first view: light to render on phones, every event in sight (as on the web).
    var focus by remember { mutableStateOf("city") }
    // Time of day, season and weather of the scene. "auto" lets the engine follow Astana's clock.
    var phase by rememberSaveable { mutableStateOf("auto") }
    var season by rememberSaveable { mutableStateOf("auto") }
    var weather by rememberSaveable { mutableStateOf("auto") }
    // «Авто» weather: real current weather in Astana, refreshed every 15 minutes.
    var live by remember { mutableStateOf<LiveWeather?>(null) }
    val night = if (phase == "auto") astanaNight(nowEpochMs()) else phase == "night"
    var curated by remember { mutableStateOf<List<CuratedEvent>>(emptyList()) }
    var selectedCurated by remember { mutableStateOf<CuratedEvent?>(null) }
    LaunchedEffect(Unit) {
        curated = try { parseCuratedEvents(Res.readBytes("files/game-city/events.json").decodeToString()) }
            catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }
    val theme = if (season == "winter") "winter" else "default"
    var place by remember { mutableStateOf<CityMessage?>(null) }
    var selectedEntranceId by remember { mutableStateOf<String?>(null) }
    var selectedVenue by remember { mutableStateOf<EventVenue?>(null) }
    var options by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(active, events, coverIds, fetchRevision) {
        // Clear immediately so replaced/deleted/private event covers cannot linger.
        covers = covers.filter { c -> c.id in coverIds && events.any { it.id == c.id && it.cover == c.ref && it.visibility == "public" } }
        if (!active) return@LaunchedEffect
        delay(180) // debounce camera movement, then at most two downloads at once
        covers = coverLoader.load(coverIds, events)
    }
    LaunchedEffect(active, fetchRevision) {
        if (!active) return@LaunchedEffect
        do {
            try {
                when (val result = repo.getEvents()) {
                    is KtorResponse.Success -> {
                        events = result.data.filter { it.visibility == "public" && it.status !in setOf("ended", "cancelled") && it.location.lat in 50.9..51.4 && it.location.lng in 71.1..71.9 }
                        eventsState = if (events.isEmpty()) "Пока нет доступных событий в Астане" else "Загружено событий: ${events.size.coerceAtMost(150)} · обновление каждые 30 с"
                    }
                    is KtorResponse.Failure -> eventsState = "Не удалось обновить события · повторить"
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { eventsState = "Нет связи с событиями · повторить" }
            delay(30_000)
        } while (true)
    }
    LaunchedEffect(active) {
        while (active) { fetchLiveWeather(coverClient)?.let { live = it }; delay(15 * 60_000L) }
    }
    LaunchedEffect(ready) { if (!ready) { delay(15_000); if (!ready) error = "3D не запустилась. Вернись к обычной карте и попробуй снова." } }
    // Without live events (API asleep or empty) the curated public list keeps the map alive.
    val useCurated = events.isEmpty() && curated.isNotEmpty()
    val cityEvents = if (useCurated) { val now = nowEpochMs(); curated.mapNotNull { it.toCityEvent(now) }.take(150) }
        else events.take(150).map { CityEvent(it.id,it.location.lat,it.location.lng,it.title,it.category,it.status,it.goingCount,it.endAt,it.expiresAtMs,
            kind=it.kind,sourceType=it.sourceType,capacity=it.capacity,confirmationCount=it.confirmationCount,startAt=it.startAt,visibility=it.visibility,coverRef=it.cover) }
    val payload = cityJson.encodeToString(CitySnapshot(manifestUrl = manifest,
        events = cityEvents,
        night=night,theme=theme,active=active,focusId=focus,focusRevision=focusRevision,updateRevision=updateRevision,
        interactionBlocked=place!=null || options || selectedCurated!=null,covers=covers,
        phase=phase,season=season,weather=if (weather == "auto") live?.kind ?: "clear" else weather))
    Box(modifier.fillMaxSize()) {
        GameCityView(payload, Modifier.fillMaxSize()) { raw ->
            parseCityMessage(raw)?.let { message ->
                when (message.type) {
                    "ready" -> { ready = true; error = null }
                    "covers" -> coverIds = message.ids.filter { id -> events.take(150).any { it.id == id && it.cover != null } }
                    "place" -> { place = message; selectedEntranceId = null; selectedVenue = message.buildingId?.let { EventVenue(it) } }
                    "event" -> if (events.any { it.id == message.id }) onOpenEvent(message.id)
                        else curated.firstOrNull { it.id == message.id }?.let { selectedCurated = it }
                    "error" -> error = "3D недоступна на этом устройстве. Обычная карта остаётся доступна."
                }
            }
        }
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(8.dp)
            .background(Color(0xED172536), RoundedCornerShape(18.dp)).padding(horizontal=8.dp,vertical=4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween, modifier=Modifier.fillMaxWidth()) {
                TextButton(onClick=onExit) { Text("2D-карта", color=Color.White) }
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("Астана",color=Color.White,fontSize=16.sp)
                    Text(listOf("Живой город", formatTemp(live?.temp)).filter { it.isNotEmpty() }.joinToString(" · "),color=Color(0xFFC9B6FF),fontSize=10.sp)
                }
                TextButton(onClick={options=true}) { Text("Вид",color=Color.White) }
            }
            Text(if (useCurated) "Подборка событий города · ${cityEvents.size}" else eventsState,color=Color(0xFFCBD6E4),fontSize=9.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(bottom=3.dp))
        }
        if (error != null) Surface(Modifier.align(Alignment.Center).padding(20.dp),shape=RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) { Text(error!!); TextButton(onClick=onExit) { Text("К обычной карте") } }
        }
    }
    if(options) ModalBottomSheet(onDismissRequest={options=false}) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Вид города",style=MaterialTheme.typography.headlineSmall)
            Text("Места",style=MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                listOf("city" to "Вся Астана", "baiterek" to "Байтерек", "arena" to "Арена").forEach { (id,title) ->
                    TextButton(onClick={focus=id;focusRevision++;options=false}) { Text(title) }
                }
            }
            Text("Другие здания: кнопка «Сцена» на карте → «Перейти к зданию».",style=MaterialTheme.typography.bodySmall)
            LookChoices("Время суток", listOf("auto" to "Авто", "sunrise" to "Рассвет", "day" to "День", "sunset" to "Закат", "night" to "Ночь"), phase) { phase = it }
            LookChoices("Сезон", listOf("auto" to "Авто", "spring" to "Весна", "summer" to "Лето", "autumn" to "Осень", "winter" to "Зима"), season) { season = it }
            LookChoices("Погода", listOf("auto" to "Авто", "clear" to "Ясно", "cloudy" to "Облачно", "rain" to "Дождь", "snow" to "Снег"), weather) { weather = it }
            val now = live?.let { w -> listOf(formatTemp(w.temp), when (w.kind) { "cloudy" -> "облачно"; "rain" -> "дождь"; "snow" -> "снег"; else -> "ясно" }).filter { it.isNotEmpty() }.joinToString(", ") }
            Text("«Авто» — по часам, календарю и реальной погоде в Астане." + (now?.let { " Сейчас: $it." } ?: "") + " Погода: $LIVE_WEATHER_CREDIT.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={updateRevision++;fetchRevision++;options=false}) { Text("Обновить карту и события") }
            Text("Персонаж пока настраивается локально. Передача местоположения друзьям здесь не включена.",style=MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
        }
    }
    selectedCurated?.let { e ->
        val uri = LocalUriHandler.current
        ModalBottomSheet(onDismissRequest={selectedCurated=null}) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(e.title,style=MaterialTheme.typography.headlineSmall)
                Text(e.whenLabel(),style=MaterialTheme.typography.titleSmall)
                val where = listOf(e.place, e.address).filter { it.isNotBlank() }.joinToString(" · ")
                if (where.isNotBlank()) Text(where,style=MaterialTheme.typography.bodyMedium)
                if (e.price.isNotBlank()) Text(e.price,style=MaterialTheme.typography.bodyMedium)
                if (e.description.isNotBlank()) Text(e.description,style=MaterialTheme.typography.bodyMedium)
                Button(onClick={ uri.openUri(e.mapUrl()) },modifier=Modifier.fillMaxWidth()) { Text("Открыть в 2ГИС") }
                if (e.url.startsWith("https://")) OutlinedButton(onClick={ uri.openUri(e.url) },modifier=Modifier.fillMaxWidth()) {
                    Text(if (e.source.isNotBlank()) "Источник: ${e.source}" else "Подробнее")
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    place?.let { p ->
        val nearby = events.filter { distanceMeters(p.lat!!, p.lng!!, it.location.lat,it.location.lng) <= 150 }.take(12)
        ModalBottomSheet(onDismissRequest={place=null}) {
            Column(Modifier.fillMaxWidth().heightIn(max=650.dp).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                CityPlaceDetails(p, selectedEntranceId) { selectedEntranceId = it }
                if (p.buildingId != null) {
                    VenueBrowser(venue=selectedVenue ?: EventVenue(p.buildingId),onSelect={selectedVenue=it},
                        active=active,onOpenEvent={id -> place=null;onOpenEvent(id)})
                } else {
                if (p.geometryKind != "point") Text("У этого объекта пока нет устойчивого идентификатора здания. Можно создать событие в выбранной точке, но не назначить ему этаж.")
                Text("События рядом · в радиусе 150 м",style=MaterialTheme.typography.titleSmall)
                if(nearby.isEmpty()) Text(if(eventsState.contains("связи") || eventsState.contains("удалось")) "Данные сейчас недоступны" else "Пока ничего нет — можно создать своё событие")
                nearby.forEach { e -> TextButton(onClick={place=null;onOpenEvent(e.id)}) { Text(e.title) } }
                }
                Button(onClick={
                    val point = p.creationPoint(selectedEntranceId)
                    place=null
                    onCreateHere(point.copy(venue=selectedVenue))
                },modifier=Modifier.fillMaxWidth()) { Text("Создать событие здесь") }
                Text("Выбор точки не подтверждает бронирование или право проводить мероприятие.",style=MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** Shared by the live place sheet and the isolated Android debug preview. */
@Composable
fun CityPlaceDetails(place: CityMessage, selectedEntranceId: String?, onSelectEntrance: (String?) -> Unit) {
    Text(place.title,style=MaterialTheme.typography.headlineSmall)
    Text(place.addressLabel(),style=MaterialTheme.typography.bodyMedium)
    if (place.id.isNotBlank()) Text("Объект: ${place.id}",style=MaterialTheme.typography.bodySmall)
    if (place.sourceRef.isNotBlank()) Text("Источник объекта: ${place.sourceRef}",style=MaterialTheme.typography.bodySmall)
    if (place.geometryKind == "stylized") Text("Стилизованная модель, не точная копия здания",style=MaterialTheme.typography.bodySmall)
    Text("Исходная точка: ${place.lat}, ${place.lng}",style=MaterialTheme.typography.bodySmall)
    Text("Входы",style=MaterialTheme.typography.titleSmall)
    Text(place.entranceStatusLabel(),style=MaterialTheme.typography.bodySmall)
    if (place.entranceStatus == CityEntranceStatus.MAPPED) {
        place.entrances.forEach { entrance ->
            OutlinedButton(onClick={onSelectEntrance(entrance.id)},modifier=Modifier.fillMaxWidth().semantics {
                selected = selectedEntranceId == entrance.id
            }) {
                Text((if (selectedEntranceId == entrance.id) "Выбран · " else "Выбрать · ") + entrance.label())
            }
        }
        if (selectedEntranceId != null) TextButton(onClick={onSelectEntrance(null)}) { Text("Сбросить выбор входа") }
    }
    Text(place.creationPoint(selectedEntranceId).selectionLabel(),style=MaterialTheme.typography.bodySmall)
}

internal fun distanceMeters(lat:Double,lng:Double,toLat:Double,toLng:Double):Double {
    val rad=PI/180; val a=sin((toLat-lat)*rad/2).pow(2)+cos(lat*rad)*cos(toLat*rad)*sin((toLng-lng)*rad/2).pow(2)
    return 6371000*2*asin(sqrt(a.coerceIn(0.0,1.0)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LookChoices(title: String, choices: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { (id, label) -> FilterChip(selected = current == id, onClick = { onPick(id) }, label = { Text(label) }) }
    }
}
