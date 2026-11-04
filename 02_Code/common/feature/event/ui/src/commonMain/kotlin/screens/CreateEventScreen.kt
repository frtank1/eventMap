package com.eventmap.event.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.event.ui.viewmodel.CreateEventViewModel
import com.eventmap.event.ui.viewmodel.CreateForm
import com.eventmap.event.ui.viewmodel.MAX_COVER_BYTES
import com.eventmap.event.ui.viewmodel.MAX_REEL_BYTES
import com.eventmap.event.ui.viewmodel.PickedMedia
import com.eventmap.event.ui.viewmodel.UploadStage
import com.eventmap.theme.*
import com.eventmap.theme.ui_kit.clickableNoRipple
import com.eventmap.map.EventMap
import com.eventmap.map.EventMapController
import com.eventmap.map.LatLng
import com.eventmap.map.MapStyle
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.eventmap.views.media.compressImage
import com.eventmap.views.media.writeTempMedia
import com.eventmap.views.video.VideoPlayer
import org.jetbrains.compose.resources.decodeToImageBitmap
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// =====================================================================
//  D6 / D7 / D8 — Создание события (3 шага)
//  Figma «🎬 D · Событие»  nodes 51:107 / 51:124 / 51:147
// =====================================================================

private val eventCategories = listOf("Музыка", "Спорт", "Еда", "Лекция", "Стендап", "Арт", "Игры", "Природа")

data class CreateEventScreen(val editEventId: String? = null) : Screen {

    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<CreateEventViewModel>()
        val step by vm.step.collectAsState()
        val form by vm.form.collectAsState()
        val loading by vm.loading.collectAsState()
        val created by vm.created.collectAsState()
        var errorText by remember { mutableStateOf<String?>(null) }

        val cover by vm.cover.collectAsState()
        val reel by vm.reel.collectAsState()
        val uploadStage by vm.uploadStage.collectAsState()
        val uploadProgress by vm.uploadProgress.collectAsState()
        val scope = rememberCoroutineScope()

        // Режим правки: префилл формы полями события (D6 переиспользуется)
        LaunchedEffect(editEventId) { editEventId?.let { vm.loadForEdit(it) } }
        LaunchedEffect(created) { if (created) nav.pop() }
        // показываем ошибки валидации/сети (раньше они молча терялись → «кнопка не работает»)
        LaunchedEffect(Unit) { vm.errorMessage.collect { errorText = it } }
        var showPicker by remember { mutableStateOf(false) }

        // Пикеры медиа (FileKit, только галерея). Фото сжимаем до ≤1600px JPEG —
        // 8 МБ с камеры превращаются в ~300 КБ, загрузка на мобильном интернете не мучает.
        fun pickCover() = scope.launch {
            val file = FileKit.pickFile(type = PickerType.Image, mode = PickerMode.Single) ?: return@launch
            val raw = file.readBytes()
            if (raw.size > 25 * 1024 * 1024) {
                errorText = "Картинка больше 25 МБ — выбери поменьше"
                return@launch
            }
            val bytes = compressImage(raw) // длинная сторона ≤1600, JPEG q85
            if (bytes.size > MAX_COVER_BYTES) {
                errorText = "Картинка слишком большая даже после сжатия — выбери другую"
                return@launch
            }
            vm.setCover(PickedMedia(bytes, "image/jpeg", file.name))
        }
        fun pickReel() = scope.launch {
            val file = FileKit.pickFile(type = PickerType.Video, mode = PickerMode.Single) ?: return@launch
            val bytes = file.readBytes()
            if (bytes.size > MAX_REEL_BYTES) {
                errorText = "Видео больше 60 МБ — нужно покороче (до 30 сек)"
                return@launch
            }
            vm.setReel(
                PickedMedia(
                    bytes, videoContentType(file.name), file.name,
                    localUrl = writeTempMedia(bytes, ".mp4"), // превью в тайле
                ),
            )
        }

        Box(Modifier.fillMaxSize().background(NightBackground)) {
        Column(
            Modifier.fillMaxSize(),
        ) {
            // Step progress bar (3 equal segments)
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 58.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(3) { i ->
                    Box(
                        Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(999.dp))
                            .background(
                                if (i <= step) Brush.linearGradient(BrandGradient)
                                else Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.12f)),
                                ),
                            ),
                    )
                }
            }

            val titles = listOf("Что за событие?", "Где это будет?", "Детали")
            val subs = listOf("Шаг 1 из 3", "Шаг 2 из 3", "Шаг 3 из 3")

            Text(
                titles[step],
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp),
            )
            Text(
                subs[step],
                color = NightTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 16.dp),
            )

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            ) {
                if (form.pendingEventId != null) {
                    Text("Событие уже создано. Осталось завершить публикацию видео; повторная попытка не создаёт новое событие.", color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    if (form.pendingReelUrl == null) {
                        MediaPickCard(
                            idleIcon = EventMapIcons.Video,
                            idleText = "Выбрать ролик для повторной загрузки",
                            height = 160.dp,
                            onClear = null,
                            pickedLabel = reel?.name,
                            onClick = { if (!loading) pickReel() },
                            videoPreviewUrl = reel?.localUrl,
                        )
                    }
                } else when (step) {
                    0 -> StepBasics(
                        kind = form.kind,
                        title = form.title,
                        description = form.description,
                        onTitle = { v -> vm.update { it.copy(title = v) } },
                        onDesc = { v -> vm.update { it.copy(description = v) } },
                        onKind = { value ->
                            vm.update {
                                it.copy(
                                    kind = value,
                                    sourceType = if (value == "live_signal") "witness" else "initiator",
                                    visibility = if (value == "private") "invite" else "public",
                                    joinPolicy = if (value == "private") "invite" else it.joinPolicy,
                                    expiresInMinutes = if (value == "live_signal" && it.expiresInMinutes.isBlank()) "120" else it.expiresInMinutes,
                                )
                            }
                        },
                        cover = cover,
                        reel = reel,
                        onPickCover = { pickCover() },
                        onPickReel = { pickReel() },
                        onClearReel = { vm.setReel(null) },
                    )
                    1 -> StepLocation(form.locationName, form.landmark, form.locationSelected,
                        onAddress = { v -> vm.update { it.copy(locationName = v, locationSelected = false) } },
                        onLandmark = { v -> vm.update { it.copy(landmark = v) } },
                        onOpenPicker = { showPicker = true },
                    )
                    else -> StepDetails(
                        form = form,
                        onDate = { v -> vm.update { it.copy(date = v) } },
                        onTime = { v -> vm.update { it.copy(time = v) } },
                        onCategory = { v -> vm.update { it.copy(category = v) } },
                        onPrice = { v -> vm.update { it.copy(priceAmount = v) } },
                        onLimit = { v -> vm.update { it.copy(limit = v) } },
                        onJoinPolicy = { v -> vm.update { it.copy(joinPolicy = v) } },
                        onVisibility = { v -> vm.update { it.copy(visibility = v) } },
                        onAudience = { v -> vm.update { it.copy(audience = v, minAge = if (v == "adults") "18" else "") } },
                        onExpires = { v -> vm.update { it.copy(expiresInMinutes = v) } },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            // Баннер ошибки (валидация/сеть)
            errorText?.let { msg ->
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF4757).copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) { Text(msg, color = Color(0xFFFF6B7A), fontSize = 13.sp) }
                Spacer(Modifier.height(8.dp))
            }

            // Bottom CTA row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (step > 0 && form.pendingEventId == null) {
                    Box(
                        Modifier.width(96.dp).height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickableNoRipple { vm.back() },
                        contentAlignment = Alignment.Center,
                    ) { Text("‹ Назад", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
                Box(
                    Modifier.weight(1f).height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(BrandGradient))
                        .clickableNoRipple {
                            if (!loading) {
                                errorText = null
                                if (step < 2 && form.pendingEventId == null) vm.next() else vm.publish()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (form.pendingEventId != null) {
                            if (loading) uploadLabel(uploadStage, uploadProgress) else "Завершить публикацию видео"
                        } else when (step) {
                            0 -> "Далее → локация"
                            1 -> "Далее → детали"
                            else -> when {
                                loading -> uploadLabel(uploadStage, uploadProgress)
                                editEventId != null -> "✓ Сохранить изменения"
                                else -> "✓ Опубликовать"
                            }
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }

        // Полноэкранный пикер точки на карте (overlay поверх формы)
        if (showPicker) {
            LocationPickerOverlay(
                initialLat = if (form.lat != 0.0) form.lat else 51.1282,
                initialLng = if (form.lng != 0.0) form.lng else 71.4307,
                onPicked = { lat, lng ->
                    vm.update { it.copy(lat = lat, lng = lng, locationSelected = true) }
                    showPicker = false
                },
                onDismiss = { showPicker = false },
            )
        }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  D6 — Шаг 1: Что за событие?
// ─────────────────────────────────────────────────────────────────────────────

@Composable
@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class) // decodeToImageBitmap
private fun StepBasics(
    kind: String,
    title: String,
    description: String,
    onTitle: (String) -> Unit,
    onDesc: (String) -> Unit,
    onKind: (String) -> Unit,
    cover: PickedMedia?,
    reel: PickedMedia?,
    onPickCover: () -> Unit,
    onPickReel: () -> Unit,
    onClearReel: () -> Unit,
) {
    FormLabel("ЧТО ТЫ СОЗДАЁШЬ")
    ChoiceChips(
        value = kind,
        options = listOf(
            "planned" to "Запланировано",
            "gathering" to "Собрать людей",
            "live_signal" to "Я это вижу",
            "private" to "Закрытое",
        ),
        onSelect = onKind,
    )
    Spacer(Modifier.height(16.dp))
    // Обложка: показываем саму картинку (декод из выбранных байтов)
    val coverBitmap = remember(cover) {
        cover?.bytes?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
    }
    MediaPickCard(
        pickedLabel = cover?.name?.let { "Обложка · $it" },
        idleIcon = EventMapIcons.Camera,
        idleText = "Добавить обложку",
        height = 160.dp,
        onClick = onPickCover,
        onClear = null,
        imagePreview = coverBitmap,
    )
    Spacer(Modifier.height(12.dp))
    // Ролик: мини-плеер из временного файла (muted, loop)
    MediaPickCard(
        pickedLabel = reel?.name?.let { "Ролик · $it" },
        idleIcon = EventMapIcons.Video,
        idleText = "Добавить ролик · до 30 сек",
        height = if (reel?.localUrl != null) 160.dp else 88.dp,
        onClick = onPickReel,
        onClear = if (reel != null) onClearReel else null,
        videoPreviewUrl = reel?.localUrl,
    )
    Spacer(Modifier.height(20.dp))
    FormLabel("НАЗВАНИЕ")
    FormInput("Напр. «Йога на рассвете»", title, onTitle)
    Spacer(Modifier.height(16.dp))
    FormLabel("ОПИСАНИЕ")
    FormInput("Расскажи, что будет, для кого…", description, onDesc, height = 80.dp)
}

// Карточка выбора медиа: пустая (тап → пикер) или с именем выбранного файла (+✕ для очистки).
@Composable
private fun MediaPickCard(
    pickedLabel: String?,
    idleIcon: ImageVector,
    idleText: String,
    height: Dp,
    onClick: () -> Unit,
    onClear: (() -> Unit)?,
    imagePreview: ImageBitmap? = null,
    videoPreviewUrl: String? = null,
) {
    val picked = pickedLabel != null
    val hasPreview = imagePreview != null || videoPreviewUrl != null
    Box(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(14.dp))
            .background(if (picked) Violet600.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Само медиа фоном тайла: картинка обложки или зацикленный ролик без звука
        if (imagePreview != null) {
            Image(
                bitmap = imagePreview,
                contentDescription = pickedLabel,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (videoPreviewUrl != null) {
            VideoPlayer(url = videoPreviewUrl, playing = true, muted = true, modifier = Modifier.fillMaxSize())
        }
        if (hasPreview) {
            // подпись поверх медиа — на градиенте, чтобы читалась
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "✓ $pickedLabel · нажми, чтобы заменить",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (picked && !hasPreview) {
                Text(
                    "✓ $pickedLabel",
                    color = Violet400,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 36.dp),
                )
                Spacer(Modifier.height(2.dp))
                Text("нажми, чтобы заменить", color = NightTextSecondary, fontSize = 11.sp)
            } else if (!picked) {
                Icon(idleIcon, contentDescription = null, tint = NightTextSecondary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(4.dp))
                Text(idleText, color = NightTextSecondary, fontSize = 13.sp)
            }
        }
        if (onClear != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp)
                    .clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                    .clickableNoRipple(onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    EventMapIcons.Close,
                    contentDescription = "Удалить",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  D7 — Шаг 2: Локация
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepLocation(
    address: String,
    landmark: String,
    picked: Boolean,
    onAddress: (String) -> Unit,
    onLandmark: (String) -> Unit,
    onOpenPicker: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0C1825))
            .clickableNoRipple(onOpenPicker),   // тап по карте → полноэкранный пикер
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).align(Alignment.Center).background(Color.White.copy(alpha = 0.08f)))
        Box(Modifier.fillMaxHeight().width(2.dp).align(Alignment.Center).background(Color.White.copy(alpha = 0.08f)))
        Icon(
            EventMapIcons.Place,
            contentDescription = null,
            tint = Violet400,
            modifier = Modifier.align(Alignment.Center).offset(y = (-14).dp).size(30.dp),
        )
        Box(
            Modifier.fillMaxWidth().padding(14.dp).height(38.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF11212F)).padding(horizontal = 13.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (picked) EventMapIcons.Place else EventMapIcons.Search,
                    contentDescription = null,
                    tint = if (picked) Violet400 else NightTextSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (picked) "Точка выбрана — нажми, чтобы изменить" else "Выбрать точку на карте",
                    color = if (picked) Violet400 else NightTextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    FormLabel("АДРЕС")
    FormInput("Триатлон-парк, главный вход", address, onAddress)
    Spacer(Modifier.height(16.dp))
    FormLabel("МЕТКА")
    FormInput("Доп. ориентир (необязательно)", landmark, onLandmark)
}

// ─────────────────────────────────────────────────────────────────────────────
//  D8 — Шаг 3: Детали + 18+
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepDetails(
    form: CreateForm,
    onDate: (String) -> Unit,
    onTime: (String) -> Unit,
    onCategory: (String) -> Unit,
    onPrice: (String) -> Unit,
    onLimit: (String) -> Unit,
    onJoinPolicy: (String) -> Unit,
    onVisibility: (String) -> Unit,
    onAudience: (String) -> Unit,
    onExpires: (String) -> Unit,
) {
    FormLabel("ДАТА")
    DateField(form.date, onDate)
    Spacer(Modifier.height(16.dp))
    FormLabel("ВРЕМЯ")
    TimeField(form.time, onTime)
    Text("Часовой пояс: ${form.timeZoneId}", color = NightTextSecondary, fontSize = 12.sp)
    Spacer(Modifier.height(16.dp))
    FormLabel("КАТЕГОРИЯ")
    Spacer(Modifier.height(8.dp))
    eventCategories.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { c ->
                val sel = form.category == c
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Violet600.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                        .clickableNoRipple { onCategory(c) }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        c,
                        color = if (sel) Violet400 else NightTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    Spacer(Modifier.height(10.dp))
    FormLabel("ЦЕНА")
    FormInput("Бесплатно", form.priceAmount, onPrice, keyboardType = KeyboardType.Number, digitsOnly = true)
    Spacer(Modifier.height(16.dp))
    FormLabel("ЛИМИТ МЕСТ")
    FormInput("∞", form.limit, onLimit, keyboardType = KeyboardType.Number, digitsOnly = true)
    Spacer(Modifier.height(16.dp))

    if (form.kind == "live_signal") {
        FormLabel("СИГНАЛ ИСЧЕЗНЕТ")
        ChoiceChips(
            value = form.expiresInMinutes,
            options = listOf("30" to "30 мин", "120" to "2 часа", "480" to "До вечера"),
            onSelect = onExpires,
        )
        Spacer(Modifier.height(16.dp))
    }

    FormLabel("КТО МОЖЕТ УВИДЕТЬ")
    ChoiceChips(
        value = form.visibility,
        options = listOf("public" to "Все", "unlisted" to "По ссылке", "invite" to "Приглашённые"),
        onSelect = onVisibility,
    )
    Spacer(Modifier.height(16.dp))

    FormLabel("КАК ВСТУПАЮТ")
    ChoiceChips(
        value = form.joinPolicy,
        options = listOf("open" to "Сразу", "approval" to "После одобрения", "invite" to "По приглашению"),
        onSelect = onJoinPolicy,
    )
    Spacer(Modifier.height(16.dp))

    FormLabel("АУДИТОРИЯ")
    ChoiceChips(
        value = form.audience,
        options = listOf("everyone" to "Для всех", "family" to "Семьи", "adults" to "18+"),
        onSelect = onAudience,
    )
}

@Composable
private fun ChoiceChips(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        options.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { (key, label) ->
                    val selected = value == key
                    Box(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(11.dp))
                            .background(if (selected) Violet600.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
                            .clickableNoRipple { onSelect(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (selected) Violet400 else NightTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Дата (календарь, только будущее) и время (часы)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(isoDate: String, onPick: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    PickerField(value = isoDate, placeholder = "Выбери дату") { show = true }
    if (show) {
        // граница «сегодня» в UTC-миллисекундах (DatePicker оперирует UTC-полуночью)
        val todayStart = remember {
            val nowMs = Clock.System.now().toEpochMilliseconds()
            (nowMs / 86_400_000L) * 86_400_000L
        }
        val state = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayStart
                override fun isSelectableYear(year: Int): Boolean = true
            },
        )
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date
                        onPick(d.toString()) // ISO yyyy-MM-dd
                    }
                    show = false
                }) { Text("Готово", color = Violet400) }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Отмена", color = NightTextSecondary) } },
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(time: String, onPick: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    PickerField(value = time, placeholder = "Выбери время") { show = true }
    if (show) {
        val state = rememberTimePickerState(initialHour = 12, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    val hh = state.hour.toString().padStart(2, '0')
                    val mm = state.minute.toString().padStart(2, '0')
                    onPick("$hh:$mm")
                    show = false
                }) { Text("Готово", color = Violet400) }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Отмена", color = NightTextSecondary) } },
            text = { TimePicker(state = state) },
        )
    }
}

// Поле-кнопка: показывает значение или плейсхолдер, по клику открывает пикер.
@Composable
private fun PickerField(value: String, placeholder: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickableNoRipple(onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            value.ifEmpty { placeholder },
            color = if (value.isEmpty()) NightTextSecondary else Color.White,
            fontSize = 14.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared form UI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormLabel(text: String) {
    Text(
        text,
        color = NightTextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun FormInput(
    placeholder: String,
    value: String,
    onValue: (String) -> Unit,
    height: Dp = 46.dp,
    keyboardType: KeyboardType = KeyboardType.Text,
    digitsOnly: Boolean = false,   // фильтр ввода: пропускать только цифры (цена, лимит)
) {
    val multiline = height > 50.dp
    Box(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 14.dp),
        contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { v -> onValue(if (digitsOnly) v.filter { it.isDigit() } else v) },
            singleLine = !multiline,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Violet400),
            modifier = Modifier.fillMaxWidth()
                .then(if (multiline) Modifier.padding(vertical = 14.dp) else Modifier),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = NightTextSecondary, fontSize = 14.sp)
                }
                inner()
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  D7a — Полноэкранный пикер точки на карте (по макету Figma)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LocationPickerOverlay(
    initialLat: Double,
    initialLng: Double,
    onPicked: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var controller by remember { mutableStateOf<EventMapController?>(null) }
    var center by remember { mutableStateOf(initialLat to initialLng) }
    var canPick by remember { mutableStateOf(false) }
    // следим за центром карты (это и есть выбираемая точка)
    LaunchedEffect(controller) {
        val map = controller ?: return@LaunchedEffect
        map.viewport.collect {
            center = it.center.lat to it.center.lon
            canPick = map.project(it.center) != null
        }
    }

    Box(Modifier.fillMaxSize().background(NightBackground)) {
        // настоящая карта Mapbox
        EventMap(
            initialCenter = LatLng(initialLat, initialLng),
            initialZoom = 14.0,
            style = MapStyle.NightDark,
            modifier = Modifier.fillMaxSize(),
            onMapReady = { controller = it },
        )

        // центральный пин — всегда по центру экрана
        Text(
            "📍",
            fontSize = 34.sp,
            modifier = Modifier.align(Alignment.Center).offset(y = (-16).dp),
        )

        // подсказка
        Box(
            Modifier.align(Alignment.Center).offset(y = 28.dp)
                .clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) { Text("Двигай карту — точка в центре", color = Color.White, fontSize = 12.sp) }

        // шапка
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 48.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clickableNoRipple(onDismiss),
                contentAlignment = Alignment.Center,
            ) { Text("‹", color = Color.White, fontSize = 30.sp) }
            Text("Выбор точки на карте", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // нижняя карточка с координатами и «Готово»
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(NightSurface)
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
        ) {
            FormLabel(if (canPick) "ВЫБРАННАЯ ТОЧКА" else "ОЖИДАНИЕ КАРТЫ")
            Text(
                if (canPick) "${formatCoord(center.first)}, ${formatCoord(center.second)}" else "Дождитесь загрузки карты или вернитесь назад",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple {
                        // Placeholder/unavailable map must not silently select Astana.
                        if (canPick && controller?.project(LatLng(center.first, center.second)) != null) {
                            onPicked(center.first, center.second)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text(if (canPick) "Выбрать эту точку" else "Карта ещё не готова", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

// Формат координаты с 4 знаками без platform-зависимого String.format (commonMain).
private fun formatCoord(v: Double): String {
    val scaled = (v * 10000).toLong()
    val whole = scaled / 10000
    val frac = (if (scaled < 0) -scaled else scaled) % 10000
    return "$whole." + frac.toString().padStart(4, '0')
}

// Подпись на кнопке во время публикации (этап загрузки медиа).
private fun uploadLabel(stage: UploadStage, progress: Float): String {
    val pct = "${(progress * 100).toInt().coerceIn(0, 100)}%"
    return when (stage) {
        UploadStage.Cover -> "Обложка… $pct"
        UploadStage.Reel -> "Ролик… $pct"
        UploadStage.Creating, UploadStage.Idle -> "Публикуем…"
    }
}

// content-type по расширению — должен совпадать с белым списком бэка (MediaUseCases).
private fun imageContentType(name: String): String =
    when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic", "heif" -> "image/heic"
        else -> "image/jpeg"
    }

private fun videoContentType(name: String): String =
    when (name.substringAfterLast('.', "").lowercase()) {
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        else -> "video/mp4"
    }
