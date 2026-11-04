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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

@kotlinx.serialization.Serializable
data class CreateForm(
    val title: String = "",
    val description: String = "",
    val locationName: String = "",
    val landmark: String = "",      // доп. ориентир (необязательно)
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val locationSelected: Boolean = false,
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
    val timeZoneId: String = TimeZone.currentSystemDefault().id,
    // Resume media after a confirmed create; never POST the same event again on this retry.
    val pendingEventId: String? = null,
    val pendingReelUrl: String? = null,
    val pendingReelMediaId: String? = null,
)

internal fun CreateForm.startInstant(): Instant =
    LocalDateTime.parse("${date}T${time}:00").toInstant(TimeZone.of(timeZoneId))

internal fun CreateForm.hasValidLocation(): Boolean = locationSelected &&
    lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0

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
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : BaseScreenModel() {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val draftOwner = drafts?.ownerId()
    private var existingEndAt = ""
    private var existingExpiresAtMs: Long? = null

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
        drafts?.load(draftOwner)?.let { saved ->
            runCatching { json.decodeFromString(CreateForm.serializer(), saved) }
                .getOrNull()?.let { _form.value = it }
        }
    }

    // Режим редактирования: id события, чьи поля правим (null = создание).
    private val _editingId = MutableStateFlow<String?>(null)
    val editingId: StateFlow<String?> = _editingId.asStateFlow()

    fun setCover(media: PickedMedia?) {
        if (!_loading.value && _form.value.pendingEventId == null) _cover.value = media
    }
    fun setReel(media: PickedMedia?) {
        if (_loading.value) return
        _reel.value = media
        _form.value = _form.value.copy(pendingReelUrl = null, pendingReelMediaId = null)
        saveDraft()
    }

    private fun saveDraft() {
        if (_editingId.value == null) drafts?.save(json.encodeToString(CreateForm.serializer(), _form.value), draftOwner)
    }

    fun update(transform: (CreateForm) -> CreateForm) {
        if (_loading.value || _form.value.pendingEventId != null) return
        _form.value = transform(_form.value)
        // автосохранение черновика (правка существующего события черновик не трогает)
        if (_editingId.value == null) {
            saveDraft()
        }
    }

    /** Префилл формы полями существующего события (экран D6 переиспользуется для правки). */
    fun loadForEdit(eventId: String) {
        if (_editingId.value == eventId) return
        _editingId.value = eventId
        screenModelScope.launch(dispatcher) {
            _loading.value = true
            when (val r = repository.getEvent(eventId)) {
                is KtorResponse.Success -> {
                    val e = r.data
                    val localStart = runCatching { Instant.parse(e.startAt).toLocalDateTime(TimeZone.currentSystemDefault()) }.getOrNull()
                    existingEndAt = e.endAt
                    existingExpiresAtMs = e.expiresAtMs
                    _form.value = CreateForm(
                        title = e.title,
                        description = e.description,
                        locationName = e.location.name,
                        lat = e.location.lat,
                        lng = e.location.lng,
                        locationSelected = true,
                        date = localStart?.date?.toString().orEmpty(),
                        time = localStart?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }.orEmpty(),
                        category = e.category,
                        isPaid = e.price.type == "paid",
                        priceAmount = if (e.price.amount > 0) e.price.amount.toString() else "",
                        limit = e.capacity?.toString().orEmpty(),
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
        if (_loading.value) return
        val f = _form.value
        when (_step.value) {
            0 -> if (f.title.isBlank()) return emitError(ErrorModel(customMessage = "Введите название"))
                 else _step.value = 1
            1 -> if (f.locationName.isBlank() || !f.hasValidLocation()) return emitError(ErrorModel(customMessage = "Укажите адрес и выберите точку на карте"))
                 else _step.value = 2
            else -> Unit
        }
    }

    fun back() { if (!_loading.value && _form.value.pendingEventId == null && _step.value > 0) _step.value -= 1 }

    fun publish() {
        if (_loading.value || _created.value) return
        clearError()
        if (drafts != null && (draftOwner == null || drafts.ownerId() != draftOwner)) {
            emitError(ErrorModel(customMessage = "Аккаунт изменился. Откройте создание события заново"))
            return
        }
        val f = _form.value
        val resume = f.pendingEventId != null
        val start = runCatching { f.startInstant() }.getOrNull()
        if (!resume) {
            val validationError = when {
                f.title.isBlank() || f.category.isBlank() -> "Заполните название и категорию"
                !f.hasValidLocation() || f.locationName.isBlank() -> "Укажите адрес и выберите точку на карте"
                start == null -> "Укажите корректные дату и время"
                _editingId.value == null && f.kind != "live_signal" && start <= Clock.System.now() -> "Время начала должно быть в будущем"
                f.priceAmount.isNotBlank() && (f.priceAmount.toIntOrNull() == null || f.priceAmount.toInt() < 0) -> "Укажите корректную цену"
                f.limit.isNotBlank() && (f.limit.toIntOrNull() == null || f.limit.toInt() !in 2..100_000) -> "Лимит участников: от 2 до 100 000"
                f.kind == "gathering" && f.limit.isBlank() -> "Укажите, сколько человек нужно собрать"
                f.kind == "live_signal" && existingExpiresAtMs == null && (f.expiresInMinutes.toLongOrNull() ?: 0) !in 1..1440 -> "Срок сигнала: от 1 до 1440 минут"
                else -> null
            }
            if (validationError != null) return emitError(ErrorModel(customMessage = validationError))
        }
        if (resume && _reel.value == null && f.pendingReelUrl == null) {
            emitError(ErrorModel(customMessage = "Событие уже создано. Выберите ролик снова, чтобы завершить загрузку"))
            return
        }
        _loading.value = true // synchronous guard against double taps
        screenModelScope.launch(dispatcher) {
            try {
                var coverMediaId: String? = null
                if (!resume) _cover.value?.let { pick ->
                    _uploadStage.value = UploadStage.Cover
                    _uploadProgress.value = 0f
                    val up = repository.uploadMedia(
                        pick.bytes, pick.contentType, kind = "image", eventId = null,
                        onProgress = { p -> _uploadProgress.value = p },
                    )
                    when (up) {
                        is KtorResponse.Success -> coverMediaId = up.data.mediaId
                        is KtorResponse.Failure -> {
                            emitError(ErrorModel(customMessage = "Обложка не загружена. Событие не опубликовано — попробуйте ещё раз"))
                            return@launch
                        }
                    }
                }

                // 2) Создаём событие (с обложкой, если загрузилась).
                _uploadStage.value = UploadStage.Creating
                val draft = CreateEventDraft(
                    title = f.title.trim(),
                    description = f.description.trim(),
                    coverMediaId = coverMediaId,
                    location = EventLocation(
                        lat = f.lat,
                        lng = f.lng,
                        name = listOf(f.locationName.trim(), f.landmark.trim())
                            .filter { it.isNotEmpty() }.joinToString(" · "),
                    ),
                    startAt = start?.toString().orEmpty(),
                    endAt = existingEndAt,
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
                        ?.let { Clock.System.now().toEpochMilliseconds() + it * 60_000L }
                        ?: existingExpiresAtMs.takeIf { f.kind == "live_signal" },
                )

                val editing = _editingId.value
                val eventId = f.pendingEventId ?: run {
                    when (val result = if (editing != null) repository.updateEvent(editing, draft) else repository.createEvent(draft)) {
                        is KtorResponse.Success -> result.data.id
                        is KtorResponse.Failure -> { emitError(result.error); return@launch }
                    }
                }
                if (_reel.value != null || resume) {
                    _form.value = _form.value.copy(pendingEventId = eventId)
                    saveDraft()
                    val videoUrl = _form.value.pendingReelUrl ?: run {
                        val pick = _reel.value ?: return@launch
                        _uploadStage.value = UploadStage.Reel
                        _uploadProgress.value = 0f
                        val up = repository.uploadMedia(
                            pick.bytes, pick.contentType, kind = "video", eventId = eventId,
                            onProgress = { p -> _uploadProgress.value = p },
                            )
                        when (up) {
                            is KtorResponse.Success -> up.data.url.also { url ->
                                _form.value = _form.value.copy(pendingReelUrl = url, pendingReelMediaId = up.data.mediaId)
                                saveDraft()
                            }
                            is KtorResponse.Failure -> {
                                emitError(ErrorModel(customMessage = "Событие создано, но видео не загружено. Повторите загрузку — новое событие создано не будет"))
                                return@launch
                            }
                        }
                    }
                    when (repository.registerReel(
                        eventId = eventId,
                        mediaUrl = videoUrl,
                        thumb = null,                 // постер-кадр — отдельным шагом (нужен платформенный API)
                        caption = f.title.trim(),
                        category = f.category,
                        mediaId = _form.value.pendingReelMediaId,
                    )) {
                        is KtorResponse.Success -> Unit
                        is KtorResponse.Failure -> {
                            emitError(ErrorModel(customMessage = "Видео загружено, но публикация ролика не завершена. Повторите — файл сохранён"))
                            return@launch
                        }
                    }
                }
                _created.value = true
                if (editing == null) drafts?.clear(draftOwner)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitError(ErrorModel(customMessage = "Не удалось завершить публикацию. Проверьте соединение и попробуйте снова"))
            } finally {
                _uploadStage.value = UploadStage.Idle
                _loading.value = false
            }
        }
    }
}
