package com.eventmap.event.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.base_screen.RequestErrorBanner
import com.eventmap.event.ui.viewmodel.*
import com.eventmap.theme.NightBackground
import com.eventmap.views.media.compressImage
import com.eventmap.views.media.writeTempMedia
import com.eventmap.views.video.VideoPlayer
import io.github.vinceglb.filekit.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

data class AddMediaScreen(val eventId: String) : Screen {
    @OptIn(ExperimentalResourceApi::class)
    @Composable override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<AddMediaViewModel>()
        val state by vm.state.collectAsState()
        val scope = rememberCoroutineScope()
        var pickerError by remember { mutableStateOf<String?>(null) }
        fun choose(video: Boolean) { scope.launch {
            try {
                val file = FileKit.pickFile(type = if (video) PickerType.Video else PickerType.Image, mode = PickerMode.Single) ?: return@launch
                val raw = file.readBytes()
                if (raw.size > (if (video) MAX_REEL_BYTES else 25 * 1024 * 1024)) {
                    pickerError = if (video) "Видео должно быть до 60 МБ" else "Фото должно быть до 25 МБ"
                    return@launch
                }
                val bytes = if (video) raw else compressImage(raw)
                if (!video && bytes.size > MAX_COVER_BYTES) {
                    pickerError = "Фото слишком большое после сжатия"
                    return@launch
                }
                val type = if (!video) "image/jpeg" else when (file.name.substringAfterLast('.').lowercase()) {
                    "mov" -> "video/quicktime"; "webm" -> "video/webm"; else -> "video/mp4"
                }
                pickerError = null
                val ext = "." + file.name.substringAfterLast('.', "mp4")
                vm.choose(PickedMedia(bytes, type, file.name, if (video) writeTempMedia(bytes, ext) else null))
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { pickerError = "Не удалось прочитать файл. Выберите другой" }
        } }
        Column(Modifier.fillMaxSize().background(NightBackground).statusBarsPadding().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TextButton(onClick = { nav.pop() }, enabled = !state.loading) { Text("Назад") }
            Text("Добавить момент в событие", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { choose(false) }, enabled = !state.loading && state.done == null) { Text("Фото") }
                Button(onClick = { choose(true) }, enabled = !state.loading && state.done == null) { Text("Видео") }
            }
            Box(Modifier.fillMaxWidth().weight(1f).background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                val pick = state.pick
                if (pick?.contentType?.startsWith("video/") == true && pick.localUrl != null) {
                    VideoPlayer(pick.localUrl, Modifier.fillMaxSize(), muted = true)
                } else if (pick != null && pick.contentType.startsWith("image/")) {
                    val bitmap = remember(pick) { runCatching { pick.bytes.decodeToImageBitmap() }.getOrNull() }
                    bitmap?.let { Image(it, "Выбранное фото", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                } else Text("Выберите фото или ролик из галереи", color = Color.White)
            }
            OutlinedTextField(state.caption, vm::caption, label = { Text("Подпись к видео") }, modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && state.done == null && state.pick?.contentType?.startsWith("video/") == true)
            pickerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            RequestErrorBanner(vm)
            state.done?.let { Text(it, color = Color.White) }
            Button(onClick = { if (state.done != null) nav.pop() else vm.publish(eventId) },
                enabled = !state.loading && state.pick != null, modifier = Modifier.fillMaxWidth()) {
                Text(when { state.done != null -> "Готово"; state.loading -> "Загрузка " + (state.progress * 100).toInt() + "%"; else -> "Добавить в событие" })
            }
        }
    }
}
