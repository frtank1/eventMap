package com.eventmap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmap.theme.EventMapTheme
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightTextSecondary
import com.eventmap.video.models.VideoItem
import com.eventmap.video.ui.screens.VideoFeedLayout

/** Standalone visual QA; intentionally never opens the real app's navigation or repositories. */
class DesignPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            EventMapTheme(darkTheme = true) {
                var state by remember { mutableStateOf("Карточка") }
                var liked by remember { mutableStateOf(false) }
                var note by remember { mutableStateOf<String?>(null) }
                Scaffold(containerColor = NightBackground) { inset ->
                    Column(Modifier.fillMaxSize().padding(inset).background(NightBackground)) {
                        Text("UI PREVIEW · ТЕСТОВЫЕ ДАННЫЕ · БЕЗ API", color = NightTextSecondary,
                            fontSize = 10.sp, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            listOf("Карточка", "Пусто", "Ошибка").forEach { label ->
                                TextButton(onClick = { state = label }) { Text(label) }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            VideoFeedLayout(
                                items = if (state == "Карточка") listOf(VideoItem(
                                    id = "preview", eventId = "preview-event", eventTitle = "Футбол. Не хватает двоих",
                                    authorName = "Данияр", caption = "Собрались после работы. Вот за такие вечера и любим город.",
                                    mediaUrl = "android.resource://$packageName/${R.raw.preview_reel}",
                                    likes = if (liked) 29 else 28, liked = liked,
                                )) else emptyList(),
                                loading = false, error = if (state == "Ошибка") "Сервер временно недоступен" else null,
                                commentCounts = mapOf("preview-event" to 4),
                                onRetry = { note = "Это UI-пример. Запросы на сервер не отправляются." },
                                onLike = { liked = !liked }, onVisible = {},
                                onOpenEvent = { note = "В приложении откроется связанное событие. Здесь только проверка интерфейса." },
                                onComments = { note = "В приложении откроются комментарии события." },
                                onShare = { note = "В приложении откроется системное меню «Поделиться»." },
                                onNotifications = { note = "В приложении откроется лента уведомлений." },
                            )
                        }
                        BottomNavBar(1, { note = "Проверка оформления навигации; рабочие экраны — в EventMap." },
                            { note = "В приложении откроется форма события. Превью ничего не создаёт." })
                    }
                }
                note?.let { text -> AlertDialog(onDismissRequest = { note = null },
                    title = { Text("Тестовый экран") }, text = { Text(text) },
                    confirmButton = { TextButton(onClick = { note = null }) { Text("Понятно") } }) }
            }
        }
    }
}
