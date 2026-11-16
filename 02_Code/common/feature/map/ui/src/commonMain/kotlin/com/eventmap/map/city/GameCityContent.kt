package com.eventmap.map.city

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlin.math.*

/** New pilot is deliberately separate from the existing provider map and its filters. */
@OptIn(ExperimentalMaterial3Api::class)
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
    var focus by remember { mutableStateOf("baiterek") }
    var night by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf("default") }
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
    LaunchedEffect(ready) { if (!ready) { delay(15_000); if (!ready) error = "3D не запустилась. Вернись к обычной карте и попробуй снова." } }
    val payload = cityJson.encodeToString(CitySnapshot(manifestUrl = manifest,
        events = events.take(150).map { CityEvent(it.id,it.location.lat,it.location.lng,it.title,it.category,it.status,it.goingCount,it.endAt,it.expiresAtMs,
            kind=it.kind,sourceType=it.sourceType,capacity=it.capacity,confirmationCount=it.confirmationCount,startAt=it.startAt,visibility=it.visibility,coverRef=it.cover) },
        night=night,theme=theme,active=active,focusId=focus,focusRevision=focusRevision,updateRevision=updateRevision,
        interactionBlocked=place!=null || options,covers=covers))
    Box(modifier.fillMaxSize()) {
        GameCityView(payload, Modifier.fillMaxSize()) { raw ->
            parseCityMessage(raw)?.let { message ->
                when (message.type) {
                    "ready" -> { ready = true; error = null }
                    "covers" -> coverIds = message.ids.filter { id -> events.take(150).any { it.id == id && it.cover != null } }
                    "place" -> { place = message; selectedEntranceId = null; selectedVenue = message.buildingId?.let { EventVenue(it) } }
                    "event" -> if (events.any { it.id == message.id }) onOpenEvent(message.id)
                    "error" -> error = "3D недоступна на этом устройстве. Обычная карта остаётся доступна."
                }
            }
        }
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(8.dp)
            .background(Color(0xED172536), RoundedCornerShape(18.dp)).padding(horizontal=8.dp,vertical=4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween, modifier=Modifier.fillMaxWidth()) {
                TextButton(onClick=onExit) { Text("Назад", color=Color.White) }
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("Астана",color=Color.White,fontSize=16.sp)
                    Text("Живой город · пилот",color=Color(0xFFC9B6FF),fontSize=10.sp)
                }
                TextButton(onClick={options=true}) { Text("Вид",color=Color.White) }
            }
            Text(eventsState,color=Color(0xFFCBD6E4),fontSize=9.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(bottom=3.dp))
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
            TextButton(onClick={night = !night}) { Text(if(night) "Включить день" else "Включить ночь") }
            TextButton(onClick={theme=when(theme){"default"->"winter";"winter"->"newyear";else->"default"}}) {
                Text("Сезон: " + when(theme){"winter"->"Зима";"newyear"->"Новый год";else->"Обычный"})
            }
            TextButton(onClick={updateRevision++;fetchRevision++;options=false}) { Text("Обновить карту и события") }
            Text("Персонаж пока настраивается локально. Передача местоположения друзьям здесь не включена.",style=MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
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
