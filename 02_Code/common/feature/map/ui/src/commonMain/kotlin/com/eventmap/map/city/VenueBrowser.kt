package com.eventmap.map.city

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.VenueRepository
import com.eventmap.event.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin
import kotlin.math.min
import kotlin.time.Clock

/** Shared by the real building sheet and creation form; no plan is a valid state. */
@Composable
fun VenueBrowser(
    venue: EventVenue,
    onSelect: (EventVenue) -> Unit,
    showEvents: Boolean = true,
    onOpenEvent: (String) -> Unit = {},
    active: Boolean = true,
    repository: VenueRepository? = null,
) {
    val repo = remember(repository) { repository ?: getKoin().get<VenueRepository>() }
    var building by remember(venue.buildingId) { mutableStateOf<VenueBuilding?>(null) }
    var events by remember(venue.buildingId) { mutableStateOf<List<EventDetail>>(emptyList()) }
    var planError by remember(venue.buildingId) { mutableStateOf<String?>(null) }
    var eventError by remember(venue.buildingId) { mutableStateOf<String?>(null) }
    var loadingPlan by remember(venue.buildingId) { mutableStateOf(true) }
    var loadingEvents by remember(venue.buildingId) { mutableStateOf(showEvents) }
    var refresh by remember(venue.buildingId) { mutableIntStateOf(0) }
    LaunchedEffect(venue.buildingId, refresh, active, repo, showEvents) {
        if (!active) return@LaunchedEffect
        launch {
            loadingPlan = true
            when (val result = repo.getBuilding(venue.buildingId)) {
                is KtorResponse.Success -> { building = result.data; planError = null }
                is KtorResponse.Failure -> planError = if (building == null) "План сейчас недоступен. Это не означает, что его нет." else "Не удалось обновить план. Показана последняя загруженная версия."
            }
            loadingPlan = false
        }
        if (showEvents) do {
            loadingEvents = true
            when (val result = repo.getEvents(venue.buildingId)) {
                is KtorResponse.Success -> { events = result.data; eventError = null }
                is KtorResponse.Failure -> eventError = "Не удалось обновить события здания."
            }
            loadingEvents = false
            delay(30_000)
        } while (true)
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
        Text("Внутри здания",style=MaterialTheme.typography.titleMedium)
        Text(building?.name ?: venue.buildingId,style=MaterialTheme.typography.bodySmall)
        if (loadingPlan && building == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        planError?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
        building?.let { b ->
            if (b.floors.isEmpty()) Text("Внутренний план пока не опубликован. События здания доступны без него.",style=MaterialTheme.typography.bodyMedium)
            else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected=venue.floorId==null,onClick={onSelect(EventVenue(venue.buildingId))},label={Text("Всё здание")})
                    b.floors.sortedBy { it.order }.forEach { floor ->
                        FilterChip(selected=venue.floorId==floor.id,onClick={onSelect(EventVenue(venue.buildingId,floor.id))},label={Text(floor.name)})
                    }
                }
                if (venue.floorId == null) Text("Выберите этаж для просмотра плана. Без выбора событие относится ко всему зданию.",style=MaterialTheme.typography.bodySmall)
                b.floors.firstOrNull { it.id == venue.floorId }?.let { floor ->
                    if (floor.plan == null) Text("Схема этого этажа пока не опубликована.")
                    else IndoorFloorPlan(floor,venue.roomId,eventsForVenue(events,EventVenue(venue.buildingId,floor.id)),onRoom={
                        onSelect(EventVenue(venue.buildingId,floor.id,it))
                    })
                    TextButton(onClick={onSelect(EventVenue(venue.buildingId,floor.id))}) { Text("Весь этаж · без помещения") }
                    floor.rooms.forEachIndexed { index, room ->
                        FilterChip(selected=venue.roomId==room.id,onClick={onSelect(EventVenue(venue.buildingId,floor.id,room.id))},
                            label={Text("${index+1}. ${room.name}")})
                    }
                }
            }
            if (!b.accepts(venue)) {
                Text("Выбранный этаж или помещение больше не доступны в плане. Заново выберите место перед публикацией.",color=MaterialTheme.colorScheme.error)
                TextButton(onClick={onSelect(EventVenue(venue.buildingId))}) { Text("Оставить только здание") }
            }
            Text("Версия плана: ${b.revision}",style=MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick={refresh++},enabled=!loadingPlan && !loadingEvents) { Text("Обновить план и события") }
        if (showEvents) {
            val visible = eventsForVenue(events,venue)
            Text(if (venue.roomId != null) "События помещения" else if (venue.floorId != null) "События этажа" else "События здания",
                style=MaterialTheme.typography.titleSmall)
            eventError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            if (loadingEvents && events.isEmpty()) Text("Загружаем события…")
            else if (visible.isEmpty() && eventError == null) Text("Пока ничего нет — можно создать своё событие")
            visible.forEach { event ->
                TextButton(onClick={onOpenEvent(event.id)},modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(event.title)
                        Text(venueLocationLabel(event.venue,building),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

internal fun eventsForVenue(events: List<EventDetail>, venue: EventVenue, now: Long = Clock.System.now().toEpochMilliseconds()) = events.filter {
    it.visibility == "public" && it.status !in setOf("ended","cancelled") && (it.expiresAtMs ?: Long.MAX_VALUE) > now &&
        it.venue?.buildingId == venue.buildingId && (venue.floorId == null || it.venue?.floorId == venue.floorId) &&
        (venue.roomId == null || it.venue?.roomId == venue.roomId)
}

internal fun venueLocationLabel(venue: EventVenue?, building: VenueBuilding?): String {
    if (venue?.floorId == null) return "Этаж не указан"
    val floor = building?.floors?.firstOrNull { it.id == venue.floorId } ?: return "Этаж ${venue.floorId} · нет в загруженном плане"
    val room = floor.rooms.firstOrNull { it.id == venue.roomId }
    return if (venue.roomId == null) floor.name else "${floor.name} · ${room?.name ?: "помещение ${venue.roomId} (не найдено)"}"
}

/** Read-only location on the event card, separate from the editable creation picker. */
@Composable
fun EventVenueLocation(venue: EventVenue) {
    val repo = remember { getKoin().get<VenueRepository>() }
    var building by remember(venue.buildingId) { mutableStateOf<VenueBuilding?>(null) }
    var failed by remember(venue.buildingId) { mutableStateOf(false) }
    var expanded by remember(venue) { mutableStateOf(false) }
    LaunchedEffect(venue.buildingId) {
        when(val result=repo.getBuilding(venue.buildingId)) {
            is KtorResponse.Success -> building=result.data
            is KtorResponse.Failure -> failed=true
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(building?.name ?: venue.buildingId,style=MaterialTheme.typography.titleSmall)
        Text(venueLocationLabel(venue,building),style=MaterialTheme.typography.bodySmall)
        if(failed)Text("План не удалось загрузить; привязка события сохранена.",style=MaterialTheme.typography.bodySmall)
        val floor=building?.floors?.firstOrNull{it.id==venue.floorId}
        if(floor?.plan!=null) {
            TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Скрыть план" else "Показать этаж")}
            if(expanded)IndoorFloorPlan(floor,venue.roomId,emptyList(),onRoom={},readOnly=true)
        }
    }
}

/** Coordinates stay in the drawing's local plane; they must never replace event lat/lng. */
internal fun roomAt(floor: VenueFloor, point: PlanPoint): String? = floor.rooms.firstOrNull { room ->
    val polygon = room.polygon
    if (polygon.size < 3) false else {
        var inside = false
        for (i in polygon.indices) {
            val a = polygon[i]; val b = polygon[(i+1)%polygon.size]
            if ((a.y > point.y) != (b.y > point.y) && point.x < (b.x-a.x)*(point.y-a.y)/(b.y-a.y)+a.x) inside = !inside
        }
        inside
    }
}?.id

@Composable
private fun IndoorFloorPlan(floor: VenueFloor, selectedRoom: String?, events: List<EventDetail>, onRoom: (String?) -> Unit, readOnly: Boolean = false) {
    val plan = floor.plan ?: return
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color=Color(0xFF172536))
    Canvas(Modifier.fillMaxWidth().height(260.dp).semantics { contentDescription=if(readOnly)"План ${floor.name}. Помещение события выделено." else "План ${floor.name}. Помещения также доступны списком ниже." }
        .pointerInput(floor,readOnly) { detectTapGestures { tap ->
            if(readOnly)return@detectTapGestures
            val scale = min(size.width/plan.width,size.height/plan.height)*.92
            val dx=(size.width-plan.width*scale)/2;val dy=(size.height-plan.height*scale)/2
            onRoom(roomAt(floor,PlanPoint((tap.x-dx)/scale,(tap.y-dy)/scale)))
        } }) {
        val scale=min(size.width/plan.width,size.height/plan.height)*.92
        val dx=(size.width-plan.width*scale)/2;val dy=(size.height-plan.height*scale)/2
        fun offset(p: PlanPoint)=Offset((dx+p.x*scale).toFloat(),(dy+p.y*scale).toFloat())
        fun path(points: List<PlanPoint>)=Path().apply {
            points.forEachIndexed { i,p -> val o=offset(p);if(i==0)moveTo(o.x,o.y)else lineTo(o.x,o.y) };close()
        }
        val outline=plan.outline.ifEmpty { listOf(PlanPoint(0.0,0.0),PlanPoint(plan.width,0.0),PlanPoint(plan.width,plan.height),PlanPoint(0.0,plan.height)) }
        drawPath(path(outline),Color(0xFFE9E4DA));drawPath(path(outline),Color(0xFF73828C),style=Stroke(2f))
        floor.rooms.forEachIndexed { index,room ->
            if(room.polygon.isNotEmpty()) {
                val roomPath=path(room.polygon)
                drawPath(roomPath,if(room.id==selectedRoom)Color(0xFFC9B6FF)else Color(0xFFCEE0DC))
                drawPath(roomPath,Color(0xFF506D77),style=Stroke(2f))
                val centre=offset(PlanPoint(room.polygon.map{it.x}.average(),room.polygon.map{it.y}.average()))
                val count=events.count { it.venue?.roomId==room.id }
                val text=textMeasurer.measure(if(count>0)"${index+1} · $count" else "${index+1}",labelStyle)
                drawText(text,topLeft=centre-Offset(text.size.width/2f,text.size.height/2f))
            }
        }
    }
    Text(if(readOnly)"Помещение события выделено на плане." else "Номер — помещение в списке; число рядом — события. Нажмите помещение для выбора.",style=MaterialTheme.typography.labelSmall)
}
