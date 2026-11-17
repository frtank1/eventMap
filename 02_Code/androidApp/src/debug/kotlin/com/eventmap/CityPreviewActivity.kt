package com.eventmap

import android.os.Bundle
import android.util.Log
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
    val locationLabel:String="",val coverImage:String="",
) {
    fun json(joined:Boolean)=JSONObject().put("id",id).put("title",title).put("category",category).put("lat",lat).put("lng",lng)
        .put("kind",kind).put("status",status).put("goingCount",count+if(joined&&kind!="live_signal")1 else 0)
        .put("capacity",capacity?:JSONObject.NULL).put("confirmationCount",confirmations+if(joined&&kind=="live_signal")1 else 0).put("visibility","public").put("coverImage",coverImage)
}

/** Isolated debug journey. No login, repositories, permission request or network writes. */
class CityPreviewActivity : ComponentActivity() {
    private var previewActive by mutableStateOf(false)
    private fun festivalEvents():List<PreviewCityEvent> {
        val data=JSONArray(assets.open("eventmap-festival/events.json").bufferedReader().use { it.readText() })
        require(data.length()==150) { "Rebuild the debug festival assets with game-city/build.mjs" }
        return (0 until data.length()).map { index -> val e=data.getJSONObject(index)
            PreviewCityEvent(e.getString("id"),e.getString("title"),e.getString("category"),e.getDouble("lat"),e.getDouble("lng"),
                e.getString("kind"),e.getString("status"),e.getInt("goingCount"),
                if(e.isNull("capacity"))null else e.getInt("capacity"),e.getInt("confirmationCount"),coverImage=e.getString("coverImage"))
        }
    }
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
                var events by remember { mutableStateOf(festivalEvents()) }
                var selectedId by remember { mutableStateOf("") }
                val selected=events.firstOrNull { it.id==selectedId }
                val joined=selectedId in joinedIds
                var place by remember { mutableStateOf<CityMessage?>(null) }
                var selectedEntranceId by remember { mutableStateOf<String?>(null) }
                var createDemo by remember { mutableStateOf(false) }
                var draftTitle by remember { mutableStateOf("") }
                var focusId by remember { mutableStateOf("city") }
                var focusRevision by remember { mutableIntStateOf(0) }
                var playing by remember { mutableStateOf(false) }
                var night by remember { mutableStateOf(true) }
                var dense by remember { mutableStateOf(true) }
                var nsdiQuarter by remember { mutableStateOf(false) }
                var trialBusy by remember { mutableStateOf(false) }
                var trialNote by remember { mutableStateOf("Текущая карта · сравнение квартала НИПД") }
                var benchmarkRevision by remember { mutableIntStateOf(0) }
                var benchmarkCommand by remember { mutableStateOf("") }
                var benchmarkRunning by remember { mutableStateOf(false) }
                var benchmarkArea by remember { mutableIntStateOf(0) }
                val benchmarkAreas=listOf("center" to "Центр","north-city" to "Север","east-city" to "Восток","west-city" to "Запад")
                var overviewReady by remember { mutableStateOf(false) }
                var benchmarkNote by remember { mutableStateOf("Подготавливаем объёмную карту для прогона…") }
                var eventTaps by remember { mutableIntStateOf(0) }
                var note by remember { mutableStateOf<String?>(null) }
                val payload=JSONObject().put("v",1).put("type","sync").put("manifestUrl","").put("preview",true)
                    .put("density",if(dense)"festival" else "normal").put("benchmarkRevision",benchmarkRevision).put("benchmarkCommand",benchmarkCommand)
                    .put("benchmarkArea",benchmarkAreas[benchmarkArea].first)
                    .put("interactionBlocked",sheet||place!=null||venueTest).put("active",previewActive).put("night",night).put("theme","default")
                    .put("focusId",focusId).put("focusRevision",focusRevision)
                    .put("quarterVariant",if(nsdiQuarter)"nsdi" else "current")
                    .put("events",JSONArray(events.map { it.json(it.id in joinedIds) })).toString()
                Column(Modifier.fillMaxSize().background(NightBackground).statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        Column(Modifier.padding(vertical=10.dp)) {
                            Text("Астана · ${events.size} событий",color=Color.White,fontSize=16.sp)
                            Text("ВЫМЫШЛЕННЫЕ · БЕЗ GPS И ЗАПИСИ В API",color=NightTextSecondary,fontSize=9.sp)
                        }
                        TextButton(onClick={night=!night}) { Text(if(night) "День" else "Ночь") }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                        TextButton(onClick={focusId="city";focusRevision++}){Text("Вся Астана")}
                        TextButton(onClick={focusId="baiterek";focusRevision++}){Text("Байтерек")}
                        TextButton(onClick={focusId="arena";focusRevision++}){Text("Арена")}
                        TextButton(onClick={venueTest=true}){Text("Этажи · тест")}
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                        TextButton(onClick={dense=!dense}){Text(if(dense)"Плотно" else "Спокойно")}
                        TextButton(enabled=overviewReady&&!trialBusy,onClick={benchmarkCommand=if(benchmarkRunning)"stop" else "start";benchmarkRevision++}) { Text(if(benchmarkRunning)"Стоп" else "Прогон зума") }
                        TextButton(enabled=!benchmarkRunning&&!trialBusy,onClick={benchmarkArea=(benchmarkArea+1)%benchmarkAreas.size;focusId=if(benchmarkArea==0)"baiterek" else benchmarkAreas[benchmarkArea].first;focusRevision++}) { Text(benchmarkAreas[benchmarkArea].second) }
                        Text("Тапы: $eventTaps",fontSize=11.sp,modifier=Modifier.padding(12.dp),color=NightTextSecondary)
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        TextButton(enabled=overviewReady&&!trialBusy&&!benchmarkRunning,onClick={nsdiQuarter=!nsdiQuarter}) {Text(if(trialBusy)"Подготовка…" else if(nsdiQuarter)"НИПД → текущая" else "Текущая → НИПД")}
                        Text(trialNote,fontSize=9.sp,color=NightTextSecondary,modifier=Modifier.weight(1f))
                    }
                    Text(benchmarkNote,fontSize=10.sp,color=NightTextSecondary,modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp))
                    GameCityView(payload,Modifier.weight(1f).fillMaxWidth()) { raw ->
                        val message=parseCityMessage(raw)
                        if(message?.type=="event" && events.any { it.id==message.id }) {
                            selectedId=message.id;sheet=true;eventTaps++
                            Log.i("EventMapCityQA","EVENT_TAP ${message.id}")
                        }
                        if(message?.type=="status"&&message.state=="benchmark-progress") {benchmarkRunning=true;benchmarkNote=message.title}
                        if(message?.type=="status"&&message.state=="overview-ready") {
                            overviewReady=true
                            if(benchmarkRevision==0) benchmarkNote="Готово: город → здание → город · 150 событий"
                        }
                        if(message?.type=="status"&&message.state=="overview-unavailable") benchmarkNote="Общий вид не загрузился. Перезапусти debug-превью."
                        if(message?.type=="status"&&message.state.startsWith("quarter-trial-")) {
                            trialBusy=message.state=="quarter-trial-loading";trialNote=message.title
                            if(!trialBusy)nsdiQuarter=JSONObject(raw).optString("variant")=="nsdi"
                            Log.i("EventMapCityQA","QUARTER_TRIAL $raw")
                        }
                        if(message?.type=="status"&&message.state=="benchmark-result") {
                            benchmarkRunning=false
                            val result=JSONObject(raw).optJSONObject("benchmark")
                            val frames=result?.optJSONObject("frameTiming")
                            benchmarkNote=if(frames!=null&&!frames.isNull("fps"))
                                "${result.optString("status")} · %.1f FPS · p95 %.1f мс · >50 мс: %d".format(java.util.Locale.US,frames.optDouble("fps"),frames.optDouble("p95Ms"),frames.optInt("slowFrames"))
                            else "Прогон остановлен: ${result?.optString("reason") ?: "нет данных"}"
                            Log.i("EventMapCityQA","ZOOM_RESULT $result")
                        }
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
                            if(events.size>=150) Text("В нагрузочном сценарии лимит 150 событий. Заверши одно демо-событие, чтобы добавить новое.",fontSize=11.sp,color=NightTextSecondary)
                            Button(enabled=draftTitle.isNotBlank()&&events.size<150,onClick={
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
