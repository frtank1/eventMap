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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eventmap.core.network.CoreConfig
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.EventRepository
import com.eventmap.event.models.EventDetail
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
    onCreateHere: (Double, Double, String) -> Unit,
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
    var eventsState by remember { mutableStateOf("Загружаем события…") }
    var fetchRevision by remember { mutableIntStateOf(0) }
    var focusRevision by remember { mutableIntStateOf(0) }
    var updateRevision by remember { mutableIntStateOf(0) }
    var focus by remember { mutableStateOf("baiterek") }
    var night by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf("default") }
    var place by remember { mutableStateOf<CityMessage?>(null) }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(active, fetchRevision) {
        if (!active) return@LaunchedEffect
        do {
            try {
                when (val result = repo.getEvents()) {
                    is KtorResponse.Success -> {
                        events = result.data.filter { it.visibility == "public" && it.location.lat in 50.9..51.4 && it.location.lng in 71.1..71.9 }
                        eventsState = if (events.isEmpty()) "Пока нет событий в этом районе" else "События: ${events.size} · обновление каждые 30 с"
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
        events = events.take(150).map { CityEvent(it.id,it.location.lat,it.location.lng) },
        night=night,theme=theme,active=active,focusId=focus,focusRevision=focusRevision,updateRevision=updateRevision))
    Box(modifier.fillMaxSize()) {
        GameCityView(payload, Modifier.fillMaxSize()) { raw ->
            parseCityMessage(raw)?.let { message ->
                when (message.type) {
                    "ready" -> { ready = true; error = null }
                    "place" -> place = message
                    "event" -> if (events.any { it.id == message.id }) onOpenEvent(message.id)
                    "error" -> error = "3D недоступна на этом устройстве. Обычная карта остаётся доступна."
                }
            }
        }
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(8.dp)
            .background(Color(0xED172536), RoundedCornerShape(18.dp)).padding(horizontal=8.dp,vertical=4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween, modifier=Modifier.fillMaxWidth()) {
                TextButton(onClick=onExit) { Text("Обычная карта", color=Color.White) }
                Text("3D · пилот",color=Color(0xFFC9B6FF),fontSize=12.sp)
                TextButton(onClick={night = !night}) { Text(if(night) "День" else "Ночь",color=Color.White) }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                listOf("baiterek" to "Байтерек", "akorda" to "Ак Орда", "arena" to "Арена").forEach { (id,title) ->
                    TextButton(onClick={focus=id;focusRevision++}) { Text(title,color=Color.White,fontSize=12.sp) }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={theme=when(theme){"default"->"winter";"winter"->"newyear";else->"default"}}) {
                    Text(when(theme){"winter"->"Зима";"newyear"->"Новый год";else->"Обычный стиль"},color=Color(0xFFC9B6FF),fontSize=11.sp)
                }
                TextButton(onClick={updateRevision++;fetchRevision++}) { Text("Обновить карту",color=Color.White,fontSize=11.sp) }
            }
            TextButton(onClick={fetchRevision++},modifier=Modifier.fillMaxWidth()) { Text(eventsState,color=Color(0xFFCBD6E4),fontSize=10.sp) }
        }
        if (error != null) Surface(Modifier.align(Alignment.Center).padding(20.dp),shape=RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(20.dp)) { Text(error!!); TextButton(onClick=onExit) { Text("К обычной карте") } }
        }
    }
    place?.let { p ->
        val nearby = events.filter { distanceMeters(p.lat!!, p.lng!!, it.location.lat,it.location.lng) <= 150 }.take(12)
        ModalBottomSheet(onDismissRequest={place=null}) {
            Column(Modifier.fillMaxWidth().heightIn(max=470.dp).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(p.title,style=MaterialTheme.typography.headlineSmall)
                Text("${p.lat.toString().take(8)}, ${p.lng.toString().take(8)}",style=MaterialTheme.typography.bodySmall)
                Text("События рядом · в радиусе 150 м",style=MaterialTheme.typography.titleSmall)
                if(nearby.isEmpty()) Text(if(eventsState.contains("связи") || eventsState.contains("удалось")) "Данные сейчас недоступны" else "Пока ничего нет — можно создать своё событие")
                nearby.forEach { e -> TextButton(onClick={place=null;onOpenEvent(e.id)}) { Text(e.title) } }
                Button(onClick={place=null;onCreateHere(p.lat!!,p.lng!!,p.title)},modifier=Modifier.fillMaxWidth()) { Text("Создать событие здесь") }
                Text("Выбор точки не подтверждает бронирование или право проводить мероприятие.",style=MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

internal fun distanceMeters(lat:Double,lng:Double,toLat:Double,toLng:Double):Double {
    val rad=PI/180; val a=sin((toLat-lat)*rad/2).pow(2)+cos(lat*rad)*cos(toLat*rad)*sin((toLng-lng)*rad/2).pow(2)
    return 6371000*2*asin(sqrt(a.coerceIn(0.0,1.0)))
}
