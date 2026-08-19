package com.eventmap.event.ui.viewmodel

import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.base_screen.BaseScreenModel
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.models.ErrorModel
import com.eventmap.event.EventRepository
import com.eventmap.event.models.CreateEventDraft
import com.eventmap.event.models.EventLocation
import com.eventmap.event.models.EventPrice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

// Центр Астаны — дефолт для координат, пока нет настоящего пикера точки на карте (design-first, Figma).
private const val ASTANA_LAT = 51.1282
private const val ASTANA_LNG = 71.4307

@kotlinx.serialization.Serializable
data class CreateForm(
    val title: String = "",
    val description: String = "",
    val locationName: String = "",
    val landmark: String = "",      // доп. ориентир (необязательно)
    val lat: Double = 0.0,          // координаты из пикера карты (0 = не выбрано → дефолт Астаны)
    val lng: Double = 0.0,
    val date: String = "",          // ISO yyyy-MM-dd (из DatePicker)
    val time: String = "",          // HH:mm (из TimePicker)
    val category: String = "",
    val isPaid: Boolean = false,
    val priceAmount: String = "",
    val limit: String = "",
    val kind: String = "planned",
    val sourceType: String = "initiator",
    val visibility: String = "public",
    val joinPolicy: String = "open",
    val audience: String = "everyone",
    val minAge: String = "",
    val expiresInMinutes: String = "",
)

// Выбранный из галереи файл (обложка/ролик): сырые байты + content-type + имя.
// Не data-класс намеренно: ByteArray не должен попадать в equals/hashCode.
class PickedMedia(
    val bytes: ByteArray,
    val contentType: String,
    val name: String,
    val localUrl: String? = null, // file://-превью (ролик) — играет VideoPlayer в тайле
) {
    val sizeBytes: Int get() = bytes.size
}

// Лимиты размера (без транскода на клиенте): защита от гигантских файлов.
const val MAX_COVER_BYTES = 8 * 1024 * 1024       // 8 МБ картинка
const val MAX_REEL_BYTES = 60 * 1024 * 1024       // 60 МБ видео (~30 сек 1080p)

// Этап публикации — для подписи на кнопке.
enum class UploadStage { Idle, Cover, Creating, Reel }

class CreateEventViewModel(
    private val repository: EventRepository,
    // null — в тестах; черновик формы переживает выход с экрана (D6)
    private val drafts: com.eventmap.core.datastore.EventDraftStorage? = null,
) : BaseScreenModel() {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private val _step = MutableStateFlow(0)         // 0..2
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _form = MutableStateFlow(CreateForm())
    val form: StateFlow<CreateForm> = _form.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _created = MutableStateFlow(false)
    val created: StateFlow<Boolean> = _created.asStateFlow()

    // Выбранные медиа (обложка/ролик). null = не выбрано (оба необязательны).
    private val _cover = MutableStateFlow<PickedMedia?>(null)
    val cover: StateFlow<PickedMedia?> = _cover.asStateFlow()

    private val _reel = MutableStateFlow<PickedMedia?>(null)
    val reel: StateFlow<PickedMedia?> = _reel.asStateFlow()

    // Текущий этап загрузки — для подписи на кнопке («Загружаем обложку…» и т.п.).
    private val _uploadStage = MutableStateFlow<UploadStage>(UploadStage.Idle)
    val uploadStage: StateFlow<UploadStage> = _uploadStage.asStateFlow()

    // Прогресс PUT в storage (0..1) — процент на кнопке, чтобы «не висело»
    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    init {
        // восстановим черновик (только режим создания; loadForEdit перезапишет форму)
        drafts?.load()?.let { saved ->
            runCatching { json.decodeFromString(CreateForm.serializer(), saved) }
                .getOrNull()?.let { _form.value = it }
        }
    }

    // Режим редактирования: id события, чьи поля правим (null = создание).
    private val _editingId = MutableStateFlow<String?>(null)
    val editingId: StateFlow<String?> = _editingId.asStateFlow()

    fun setCover(media: PickedMedia?) { _cover.value = media }
    fun setReel(media: PickedMedia?) { _reel.value = media }

    fun update(transform: (CreateForm) -> CreateForm) {
        _form.value = transform(_form.value)
        // автосохранение черновика (правка существующего события черновик не трогает)
        if (_editingId.value == null) {
            drafts?.save(json.encodeToString(CreateForm.serializer(), _form.value))
        }
    }

    /** Префилл формы полями существующего события (экран D6 переиспользуется для правки). */
    fun loadForEdit(eventId: String) {
        if (_editingId.value == eventId) return
        _editingId.value = eventId
        screenModelScope.launch {
            _loading.value = true
            when (val r = repository.getEvent(eventId)) {
                is KtorResponse.Success -> {
                    val e = r.data
                    _form.value = CreateForm(
                        title = e.title,
                        description = e.description,
                        locationName = e.location.name,
                        lat = e.location.lat,
                        lng = e.location.lng,
                        date = e.startAt.take(10),
                        time = if (e.startAt.length >= 16) e.startAt.substring(11, 16) else "",
                        category = e.category,
                        isPaid = e.price.type == "paid",
                        priceAmount = if (e.price.amount > 0) e.price.amount.toString() else "",
                        limit = "",
                        kind = e.kind,
                        sourceType = e.sourceType,
                        visibility = e.visibility,
                        joinPolicy = e.joinPolicy,
                        audience = e.audience,
                        minAge = e.minAge?.toString().orEmpty(),
                        expiresInMinutes = "",
                    )
                }
                is KtorResponse.Failure -> emitError(r.error)
            }
            _loading.value = false
        }
    }

    fun next() {
        val f = _form.value
        when (_step.value) {
            0 -> if (f.title.isBlank()) return emitError(ErrorModel(customMessage = "Введите название"))
                 else _step.value = 1
            1 -> if (f.locationName.isBlank()) return emitError(ErrorModel(customMessage = "Укажите локацию"))
                 else _step.value = 2
            else -> Unit
        }
    }

    fun back() { if (_step.value > 0) _step.value -= 1 }

    fun publish() {
        val f = _form.value
        if (f.date.isBlank() || f.category.isBlank()) {
            emitError(ErrorModel(customMessage = "Заполните дату и категорию"))
            return
        }
        screenModelScope.launch {
            _loading.value = true

            // 1) Обложка — необязательна. Если хранилище не настроено (503) или ошибка —
            //    создаём событие без обложки, не роняя публикацию.
            var coverMediaId: String? = null
            _cover.value?.let { pick ->
                _uploadStage.value = UploadStage.Cover
                _uploadProgress.value = 0f
                val up = repository.uploadMedia(
                    pick.bytes, pick.contentType, kind = "image", eventId = null,
                    onProgress = { p -> _uploadProgress.value = p },
                )
                if (up is KtorResponse.Success) coverMediaId = up.data.mediaId
            }

            // 2) Создаём событие (с обложкой, если загрузилась).
            _uploadStage.value = UploadStage.Creating
            val draft = CreateEventDraft(
                title = f.title.trim(),
                description = f.description.trim(),
                coverMediaId = coverMediaId,
                // Координаты: из пикера карты, если выбрана точка; иначе центр Астаны + разброс (±~2 км).
                location = EventLocation(
                    lat = if (f.lat != 0.0) f.lat else ASTANA_LAT + Random.nextDouble(-0.02, 0.02),
                    lng = if (f.lng != 0.0) f.lng else ASTANA_LNG + Random.nextDouble(-0.02, 0.02),
                    name = listOf(f.locationName.trim(), f.landmark.trim())
                        .filter { it.isNotEmpty() }.joinToString(" · "),
                ),
                // date — ISO yyyy-MM-dd, time — HH:mm → валидный ISO-8601
                startAt = "${f.date}T${f.time.ifBlank { "00:00" }}:00Z",
                endAt = "",
                category = f.category,
                price = (f.priceAmount.toIntOrNull() ?: 0).let { amount ->
                    if (amount > 0) EventPrice("paid", amount, "KZT") else EventPrice("free")
                },
                limit = f.limit.toIntOrNull(),
                kind = f.kind,
                sourceType = f.sourceType,
                visibility = f.visibility,
                joinPolicy = f.joinPolicy,
                audience = f.audience,
                minAge = f.minAge.toIntOrNull(),
                expiresAtMs = f.expiresInMinutes.toLongOrNull()
                    ?.takeIf { f.kind == "live_signal" }
                    ?.let { Clock.System.now().toEpochMilliseconds() + it * 60_000L },
            )

            val editing = _editingId.value
            val result = if (editing != null) repository.updateEvent(editing, draft) else repository.createEvent(draft)
            when (val r = result) {
                is KtorResponse.Success -> {
                    // 3) Ролик — необязателен, грузим после создания (нужен eventId).
                    val eventId = r.data.id
                    _reel.value?.let { pick ->
                        _uploadStage.value = UploadStage.Reel
                        _uploadProgress.value = 0f
                        val up = repository.uploadMedia(
                            pick.bytes, pick.contentType, kind = "video", eventId = eventId,
                            onProgress = { p -> _uploadProgress.value = p },
                        )
                        if (up is KtorResponse.Success) {
                            repository.registerReel(
                                eventId = eventId,
                                mediaUrl = up.data.url,
                                thumb = null,                 // постер-кадр — отдельным шагом (нужен платформенный API)
                                caption = f.title.trim(),
                                category = f.category,
                            )
                        }
                    }
                    _uploadStage.value = UploadStage.Idle
                    _loading.value = false
                    _created.value = true
                    if (editing == null) drafts?.clear() // черновик выполнил свою миссию
                }
                is KtorResponse.Failure -> {
                    _uploadStage.value = UploadStage.Idle
                    _loading.value = false
                    // событие НЕ создано — показываем ошибку, экран остаётся открытым
                    emitError(r.error)
                }
            }
        }
    }
}
