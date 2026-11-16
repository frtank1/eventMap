package com.eventmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmap.map.city.GameCityView
import com.eventmap.map.city.CityMessage
import com.eventmap.map.city.CityPlaceDetails
import com.eventmap.map.city.creationPoint
import com.eventmap.map.city.parseCityMessage
import com.eventmap.theme.EventMapTheme
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightTextSecondary
import com.eventmap.views.video.VideoPlayer
import org.json.JSONObject
import org.json.JSONArray

private data class PreviewCityEvent(
    val id:String,val title:String,val category:String,val lat:Double,val lng:Double,
    val kind:String="planned",val status:String="soon",val count:Int=0,val capacity:Int?=null,val confirmations:Int=0,
    val locationLabel:String="",
) {
    fun json(joined:Boolean)=JSONObject().put("id",id).put("title",title).put("category",category).put("lat",lat).put("lng",lng)
        .put("kind",kind).put("status",status).put("goingCount",count+if(joined&&kind!="live_signal")1 else 0)
        .put("capacity",capacity?:JSONObject.NULL).put("confirmationCount",confirmations+if(joined&&kind=="live_signal")1 else 0).put("visibility","public")
}

/** Isolated debug journey. No login, repositories, permission request or network writes. */
class CityPreviewActivity : ComponentActivity() {
    private var previewActive by mutableStateOf(false)
    override fun onResume(){super.onResume();previewActive=true}
    override fun onPause(){previewActive=false;super.onPause()}
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EventMapTheme(darkTheme=true) {
                var sheet by remember { mutableStateOf(false) }
                var venueTest by remember { mutableStateOf(false) }
                var joinedIds by remember { mutableStateOf(setOf<String>()) }
                var events by remember { mutableStateOf(listOf(
                    PreviewCityEvent("preview-football","Футбол · нужны двое","Спорт",51.12755,71.42972,"gathering",count=4,capacity=6),
                    PreviewCityEvent("preview-concert","Открытая сцена","Музыка",51.12764,71.43135,status="live",count=28),
                    PreviewCityEvent("preview-market","Фермерский маркет","Маркет",51.12840,71.42934,status="live"),
                    PreviewCityEvent("preview-telescope","Смотрим на звёзды","Находки",51.12862,71.43156,"live_signal","live",confirmations=3),
                    PreviewCityEvent("preview-route","Утренняя прогулка","Маршруты",51.12910,71.43020,"gathering",count=5,capacity=12),
                    PreviewCityEvent("preview-quest","Найди детали города","Квесты",51.12746,71.43220),
                    PreviewCityEvent("preview-arena","Астана — Тобол · пример","Спорт",51.10806,71.4025,status="live",count=120),
                    PreviewCityEvent("preview-expo","Музыка у EXPO · пример","Музыка",51.08865,71.41690,status="live",count=18),
                    PreviewCityEvent("preview-khan","Маркет у Хан Шатыра · пример","Маркет",51.13100,71.40420,status="live"),
                    PreviewCityEvent("preview-north","Пробежка · север города","Маршруты",51.205,71.43,"gathering",count=5,capacity=12),
                )) }
                var selectedId by remember { mutableStateOf("preview-football") }
                val selected=events.firstOrNull { it.id==selectedId }
                val joined=selectedId in joinedIds
                var place by remember { mutableStateOf<CityMessage?>(null) }
                var selectedEntranceId by remember { mutableStateOf<String?>(null) }
                var createDemo by remember { mutableStateOf(false) }
                var draftTitle by remember { mutableStateOf("") }
                var focusId by remember { mutableStateOf("baiterek") }
                var focusRevision by remember { mutableIntStateOf(0) }
                var playing by remember { mutableStateOf(false) }
                var night by remember { mutableStateOf(false) }
                var note by remember { mutableStateOf<String?>(null) }
                val payload=JSONObject().put("v",1).put("type","sync").put("manifestUrl","").put("preview",true)
                    .put("interactionBlocked",sheet||place!=null||venueTest).put("active",previewActive).put("night",night).put("theme","default")
                    .put("focusId",focusId).put("focusRevision",focusRevision)
                    .put("events",JSONArray(events.map { it.json(it.id in joinedIds) })).toString()
                Column(Modifier.fillMaxSize().background(NightBackground).statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        Column(Modifier.padding(vertical=10.dp)) {
                            Text("Астана · карта / этап 1",color=Color.White,fontSize=16.sp)
                            Text("ДЕМО · БЕЗ GPS И ЗАПИСИ В API",color=NightTextSecondary,fontSize=9.sp)
                        }
                        TextButton(onClick={night=!night}) { Text(if(night) "День" else "Ночь") }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                        TextButton(onClick={focusId="city";focusRevision++}){Text("Вся Астана")}
                        TextButton(onClick={focusId="baiterek";focusRevision++}){Text("Байтерек")}
                        TextButton(onClick={focusId="arena";focusRevision++}){Text("Арена")}
                        TextButton(onClick={venueTest=true}){Text("Этажи · тест")}
                    }
                    GameCityView(payload,Modifier.weight(1f).fillMaxWidth()) { raw ->
                        val message=parseCityMessage(raw)
                        if(message?.type=="event" && events.any { it.id==message.id }) {selectedId=message.id;sheet=true}
                        if(message?.type=="place") {place=message;selectedEntranceId=null;createDemo=false;draftTitle=""}
                        if(message?.type=="error") note="3D недоступна: ${message.state}."
                    }
                }
                if(venueTest) ModalBottomSheet(onDismissRequest={venueTest=false},containerColor=NightBackground) { VenuePreview() }
                if(sheet&&selected!=null) ModalBottomSheet(onDismissRequest={sheet=false;playing=false},containerColor=NightBackground) {
                    Column(Modifier.fillMaxWidth().heightIn(max=620.dp).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("ПРИМЕР СОБЫТИЯ · НЕ РЕАЛЬНОЕ ПРИГЛАШЕНИЕ",color=NightTextSecondary,fontSize=9.sp)
                        Text(selected.title,style=MaterialTheme.typography.headlineSmall)
                        if(selected.locationLabel.isNotBlank()) Text("${selected.locationLabel}\n${selected.lat}, ${selected.lng}",color=NightTextSecondary,fontSize=12.sp)
                        Text("${selected.category} · ${if(selected.status=="live")"Идёт сейчас" else "Запланировано"}\nТестовый сценарий, не настоящее мероприятие.",color=NightTextSecondary,fontSize=13.sp)
                        Text(if(selected.kind=="live_signal")"Подтверждений: ${selected.confirmations+if(joined)1 else 0}" else "Планируют прийти: ${selected.count+if(joined)1 else 0}${selected.capacity?.let { " из $it" }.orEmpty()}",style=MaterialTheme.typography.titleSmall)
                        Text("Участники и активность вымышленные. Миниатюра — условный значок категории, не подтверждение реального сооружения.",color=NightTextSecondary,fontSize=12.sp)
                        Box(Modifier.fillMaxWidth().height(160.dp)) {
                            VideoPlayer(url="android.resource://$packageName/${R.raw.preview_reel}",playing=playing,muted=true,modifier=Modifier.fillMaxSize())
                        }
                        TextButton(onClick={playing=!playing}) { Text(if(playing) "Пауза ролика" else "Посмотреть тестовый ролик") }
                        Text("Ролик — локальная графическая заглушка для проверки плеера, не съёмка события.",fontSize=10.sp,color=NightTextSecondary)
                        Button(onClick={joinedIds=if(joined)joinedIds-selectedId else joinedIds+selectedId},modifier=Modifier.fillMaxWidth()) { Text(if(joined) "Отменить · демо" else if(selected.kind=="live_signal")"Подтвердить · демо" else "Присоединиться · демо") }
                        TextButton(onClick={events=events.filterNot { it.id==selectedId };sheet=false;playing=false}){Text("Завершить событие · демо")}
                        Text("Нажатие меняет только этот пример. Никто не получит заявку или твоё местоположение.",fontSize=11.sp,color=NightTextSecondary)
                        Spacer(Modifier.height(12.dp))
                    }
                }
                place?.let { p -> ModalBottomSheet(onDismissRequest={place=null;createDemo=false},containerColor=NightBackground){
                    Column(Modifier.fillMaxWidth().heightIn(max=620.dp).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        CityPlaceDetails(p,selectedEntranceId) { selectedEntranceId=it }
                        Text("Выбор места не подтверждает бронирование или разрешение на мероприятие.",fontSize=12.sp)
                        if(!createDemo)Button(onClick={createDemo=true}){Text("Создать здесь · демо")}
                        else {
                            Text(p.creationPoint(selectedEntranceId).locationLabel,color=NightTextSecondary,fontSize=12.sp)
                            OutlinedTextField(value=draftTitle,onValueChange={draftTitle=it.take(120)},label={Text("Название тестовой встречи")},modifier=Modifier.fillMaxWidth())
                            Button(enabled=draftTitle.isNotBlank(),onClick={
                                val point=p.creationPoint(selectedEntranceId)
                                events=events+PreviewCityEvent("preview-created-${System.nanoTime()}",draftTitle.trim(),"Встречи",point.lat,point.lng,"gathering",capacity=6,locationLabel=point.locationLabel)
                                place=null;createDemo=false
                            }){Text("Добавить на демо-карту")}
                        }
                        Text("Только память этого экрана, без API. В обычном приложении открывается настоящая форма создания события.",fontSize=11.sp,color=NightTextSecondary)
                        Spacer(Modifier.height(20.dp))
                    }
                }}
                note?.let { text -> AlertDialog(onDismissRequest={note=null},text={Text(text)},confirmButton={TextButton(onClick={note=null}){Text("Понятно")}}) }
            }
        }
    }
}
