package com.eventmap

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.VenueRepository
import com.eventmap.event.models.*
import com.eventmap.map.city.VenueBrowser

/** Debug-only UI fixture, not a surveyed building or a substitute for HTTP integration tests. */
@Composable
internal fun VenuePreview() {
    val buildingId = "landmark:mega"
    var version by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(EventVenue(buildingId)) }
    var events by remember { mutableStateOf(listOf(
        EventDetail("test-building", "Встреча в здании · тест", venue=EventVenue(buildingId)),
        EventDetail("test-room", "Мастер-класс в зале · тест", venue=EventVenue(buildingId,"l1","hall")),
    )) }
    var opened by remember { mutableStateOf<String?>(null) }
    val repo = remember(version,events) {
        val floors = if(version==0) emptyList() else listOf(
            VenueFloor("l1","1 этаж",1,VenuePlan(100.0,60.0),listOf(
                VenueRoom("hall","Зал",listOf(PlanPoint(5.0,5.0),PlanPoint(55.0,5.0),PlanPoint(55.0,50.0),PlanPoint(5.0,50.0))),
                VenueRoom("cafe","Кафе",listOf(PlanPoint(60.0,5.0),PlanPoint(95.0,5.0),PlanPoint(95.0,50.0),PlanPoint(60.0,50.0))),
            )),
        ) + if(version>1) listOf(VenueFloor("l2","2 этаж",2)) else emptyList()
        val building=VenueBuilding(buildingId=buildingId,revision="test-$version",name="Тестовая схема · НЕ план MEGA",floors=floors)
        object : VenueRepository {
            override suspend fun getBuilding(buildingId: String)=KtorResponse.Success(building)
            override suspend fun getEvents(buildingId: String)=KtorResponse.Success(events)
        }
    }
    Column(Modifier.fillMaxWidth().heightIn(max=680.dp).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("ПЛАНЫ · ИЗОЛИРОВАННЫЙ UI-ТЕСТ",style=MaterialTheme.typography.labelSmall)
        Text("Вымышленная схема. Изменения только в памяти, без API и публикации.",style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={version=0}) { Text("Без плана") }
            TextButton(onClick={version=1}) { Text("План v1") }
            TextButton(onClick={version=2}) { Text("План v2") }
        }
        VenueBrowser(selected,onSelect={selected=it},repository=repo,onOpenEvent={id->opened=events.firstOrNull{it.id==id}?.title})
        Text("Выбрано: ${selected.floorId ?: "всё здание"} / ${selected.roomId ?: "без помещения"}")
        Button(onClick={
            events=events+EventDetail("test-created-${events.size}","Новая встреча ${events.size} · тест",venue=selected)
        },modifier=Modifier.fillMaxWidth()) { Text("Создать в выбранном месте · тест") }
        opened?.let { Text("Открыто событие: $it") }
        Spacer(Modifier.height(20.dp))
    }
}
