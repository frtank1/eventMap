package com.eventmap.business.ui.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.eventmap.business.ui.data.BusinessApplyBody
import com.eventmap.business.ui.data.BusinessDocNet
import com.eventmap.business.ui.data.BusinessNet
import com.eventmap.business.ui.data.BusinessRepository
import com.eventmap.business.ui.data.CreateQuestBody
import com.eventmap.business.ui.data.QuestPointBody
import com.eventmap.business.ui.data.UpdatePinBody
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.models.ErrorModel
import com.eventmap.core.platform.geo.GeoProvider
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// =====================================================================
//  M · Бизнес: гейт (есть ли аккаунт), заявка M2–M5, статус M6
//  Q · Конструктор промо-квеста (только approved-бизнес)
// =====================================================================

private fun ErrorModel.userText(fallback: String): String =
    error?.message ?: customMessage?.takeIf { it.isNotBlank() } ?: fallback

private fun ErrorModel.isNoBusiness(): Boolean =
    status == 404 || error?.code == "NO_BUSINESS"

// ── Гейт + статус: один VM, оба экрана читают GET /business/me ──

sealed interface BusinessMeState {
    data object Loading : BusinessMeState
    data object NoBusiness : BusinessMeState
    data class Has(val biz: BusinessNet) : BusinessMeState
    data class Error(val message: String) : BusinessMeState
}

class BusinessMeViewModel(private val repository: BusinessRepository) : ScreenModel {

    private val _state = MutableStateFlow<BusinessMeState>(BusinessMeState.Loading)
    val state: StateFlow<BusinessMeState> = _state.asStateFlow()

    init { load() }

    fun load() {
        screenModelScope.launch {
            _state.value = BusinessMeState.Loading
            _state.value = when (val r = repository.me()) {
                is KtorResponse.Success -> BusinessMeState.Has(r.data)
                is KtorResponse.Failure ->
                    if (r.error.isNoBusiness()) BusinessMeState.NoBusiness
                    else BusinessMeState.Error(r.error.userText("Не удалось загрузить — проверь сеть"))
            }
        }
    }
}

// ── Заявка (4 шага, черновик живёт в VM — шаги его не теряют) ──

data class RegDraft(
    val bin: String = "",
    val legalName: String = "",
    val brand: String = "",
    val phone: String = "",
    val social: String = "",
    val category: String? = null, // подпись категории («Кафе», «Музыка», …)
    val pinColorHex: String = "#7C5CFF",
    val pinAnimIndex: Int = 0, // визуальный выбор; применится после одобрения
    val docs: List<BusinessDocNet> = emptyList(),
    val agreed: Boolean = false,
) {
    val binValid: Boolean get() = bin.length == 12 && bin.all { it.isDigit() }
}

sealed interface SubmitState {
    data object Idle : SubmitState
    data object Sending : SubmitState
    data class Done(val biz: BusinessNet) : SubmitState
    data class Error(val message: String) : SubmitState
}

class BusinessRegViewModel(private val repository: BusinessRepository) : ScreenModel {

    private val _draft = MutableStateFlow(RegDraft())
    val draft: StateFlow<RegDraft> = _draft.asStateFlow()

    private val _submit = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submit: StateFlow<SubmitState> = _submit.asStateFlow()

    private val _docUploading = MutableStateFlow(false)
    val docUploading: StateFlow<Boolean> = _docUploading.asStateFlow()

    private val _docError = MutableStateFlow<String?>(null)
    val docError: StateFlow<String?> = _docError.asStateFlow()

    fun update(transform: (RegDraft) -> RegDraft) {
        _draft.value = transform(_draft.value)
    }

    fun setDocError(message: String?) {
        _docError.value = message
    }

    /** Фото/скан документа → storage; успех добавляет (kind → url) в черновик */
    fun uploadDocument(kind: String, bytes: ByteArray, contentType: String) {
        screenModelScope.launch {
            _docUploading.value = true
            _docError.value = null
            when (val r = repository.uploadDocument(bytes, contentType)) {
                is KtorResponse.Success ->
                    update { it.copy(docs = it.docs.filterNot { d -> d.kind == kind } + BusinessDocNet(kind, r.data)) }
                is KtorResponse.Failure ->
                    _docError.value = r.error.userText("Не удалось загрузить документ — проверь сеть")
            }
            _docUploading.value = false
        }
    }

    fun submit() {
        val d = _draft.value
        val category = d.category
        when {
            !d.binValid -> { _submit.value = SubmitState.Error("БИН/ИИН — ровно 12 цифр"); return }
            d.legalName.isBlank() -> { _submit.value = SubmitState.Error("Укажи юридическое название"); return }
            d.brand.isBlank() -> { _submit.value = SubmitState.Error("Укажи название бренда"); return }
            category == null -> { _submit.value = SubmitState.Error("Выбери категорию"); return }
            !d.agreed -> { _submit.value = SubmitState.Error("Подтверди полномочия представителя"); return }
        }
        if (_submit.value is SubmitState.Sending) return
        screenModelScope.launch {
            _submit.value = SubmitState.Sending
            val body = BusinessApplyBody(
                bin = d.bin,
                legalName = d.legalName.trim(),
                brandName = d.brand.trim(),
                category = category!!,
                phone = d.phone.trim().ifBlank { null },
                social = d.social.trim().ifBlank { null },
                documents = d.docs,
            )
            when (val r = repository.apply(body)) {
                is KtorResponse.Success -> {
                    // выбранный цвет пина — сразу в профиль (анимация — после одобрения)
                    repository.updatePin(UpdatePinBody(pinColor = d.pinColorHex))
                    _submit.value = SubmitState.Done(r.data)
                }
                is KtorResponse.Failure ->
                    _submit.value = SubmitState.Error(r.error.userText("Не удалось отправить заявку — проверь сеть"))
            }
        }
    }

    fun resetError() {
        if (_submit.value is SubmitState.Error) _submit.value = SubmitState.Idle
    }
}

// ── Конструктор квеста ──

data class DraftPoint(
    val placeName: String = "",
    val condition: String = "geo", // geo | photo | purchase
    val lat: Double? = null,
    val lng: Double? = null,
)

data class QuestDraft(
    val title: String = "",
    val emoji: String = "🎯",
    val prize: String = "",
    val prizeCount: String = "",
    val participantLimit: String = "",
    val days: Int = 30,
    val orderedChain: Boolean = false,
    val points: List<DraftPoint> = listOf(DraftPoint()),
)

sealed interface QuestSubmitState {
    data object Idle : QuestSubmitState
    data object Sending : QuestSubmitState
    data class Done(val title: String) : QuestSubmitState
    data class Error(val message: String) : QuestSubmitState
}

class QuestBuilderViewModel(
    private val repository: BusinessRepository,
    private val geo: GeoProvider,
) : ScreenModel {

    private val _draft = MutableStateFlow(QuestDraft())
    val draft: StateFlow<QuestDraft> = _draft.asStateFlow()

    private val _submit = MutableStateFlow<QuestSubmitState>(QuestSubmitState.Idle)
    val submit: StateFlow<QuestSubmitState> = _submit.asStateFlow()

    /** индекс точки, для которой сейчас берём геолокацию; null — не берём */
    private val _locating = MutableStateFlow<Int?>(null)
    val locating: StateFlow<Int?> = _locating.asStateFlow()

    fun update(transform: (QuestDraft) -> QuestDraft) {
        _draft.value = transform(_draft.value)
    }

    fun updatePoint(index: Int, transform: (DraftPoint) -> DraftPoint) {
        update { d ->
            d.copy(points = d.points.mapIndexed { i, p -> if (i == index) transform(p) else p })
        }
    }

    fun addPoint() {
        update { d -> if (d.points.size >= 10) d else d.copy(points = d.points + DraftPoint()) }
    }

    fun removePoint(index: Int) {
        update { d -> if (d.points.size <= 1) d else d.copy(points = d.points.filterIndexed { i, _ -> i != index }) }
    }

    /** «Я на месте» — координаты точки из геолокации телефона */
    fun locatePoint(index: Int) {
        if (_locating.value != null) return
        screenModelScope.launch {
            _locating.value = index
            val fix = geo.current()
            if (fix != null) {
                updatePoint(index) { it.copy(lat = fix.lat, lng = fix.lng) }
            } else {
                _submit.value = QuestSubmitState.Error("Геолокация недоступна — разреши доступ в настройках")
            }
            _locating.value = null
        }
    }

    fun submit() {
        val d = _draft.value
        when {
            d.title.isBlank() -> { _submit.value = QuestSubmitState.Error("Название обязательно"); return }
            d.prize.isBlank() -> { _submit.value = QuestSubmitState.Error("Награда обязательна — ради неё и играют"); return }
            d.points.any { it.lat == null || it.lng == null } ->
                { _submit.value = QuestSubmitState.Error("У каждой точки нужны координаты — нажми «Я на месте»"); return }
        }
        if (_submit.value is QuestSubmitState.Sending) return
        screenModelScope.launch {
            _submit.value = QuestSubmitState.Sending
            val body = CreateQuestBody(
                title = d.title.trim(),
                emoji = d.emoji,
                orderedChain = d.orderedChain,
                startsAt = isoToday(),
                endsAt = isoTodayPlus(d.days),
                prizeTitle = d.prize.trim(),
                prizeCount = d.prizeCount.trim().toIntOrNull(),
                participantLimit = d.participantLimit.trim().toIntOrNull(),
                points = d.points.map {
                    QuestPointBody(
                        lat = it.lat!!, lng = it.lng!!,
                        placeName = it.placeName.trim().ifBlank { null },
                        condition = it.condition,
                    )
                },
            )
            when (val r = repository.createQuest(body)) {
                is KtorResponse.Success -> _submit.value = QuestSubmitState.Done(d.title.trim())
                is KtorResponse.Failure ->
                    _submit.value = QuestSubmitState.Error(r.error.userText("Не удалось создать квест — проверь сеть"))
            }
        }
    }

    fun resetError() {
        if (_submit.value is QuestSubmitState.Error) _submit.value = QuestSubmitState.Idle
    }

    private fun isoToday(): String = isoTodayPlus(0)

    private fun isoTodayPlus(days: Int): String =
        Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds() + days * 86_400_000L)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString() // ISO yyyy-MM-dd
}
