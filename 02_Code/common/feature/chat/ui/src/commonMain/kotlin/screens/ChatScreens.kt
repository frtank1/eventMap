package com.eventmap.chat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  Сообщения
//  ConversationListScreen — список диалогов
//  ConversationScreen    — конкретный чат
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)

private data class DialogItem(
    val id: String,
    val name: String,
    val avatarEmoji: String,
    val lastMessage: String,
    val time: String,
    val unread: Int = 0,
    val isOnline: Boolean = false,
    val isEvent: Boolean = false,
)

private val sampleDialogs = listOf(
    DialogItem("d1", "Тимур Ахметов", "😎", "Увидимся на джаз-концерте?", "20:14", 2, isOnline = true),
    DialogItem("d2", "Jazz Квартал · Алатау", "🎵", "Ваш билет подтверждён ✓", "19:45", 0, isEvent = true),
    DialogItem("d3", "Зарина Кадырова", "🥰", "Это был классный маркет!", "18:02", 0),
    DialogItem("d4", "Craft Маркет «Весна»", "🛍", "Участников: 831 · Завтра с 11:00", "17:30", 1, isEvent = true),
    DialogItem("d5", "Данияр Асанов", "🤓", "Ты был на стендапе вчера?", "14:11", 0),
    DialogItem("d6", "Арайлым", "😄", "Отличное фото с события!", "Вчера", 0),
    DialogItem("d7", "Comedy Club KZ", "🎤", "Скоро новый сезон! 🎉", "Пн", 3, isEvent = true),
)

// ─────────────────────────────────────────────────────────────────────────────
//  ConversationListScreen
// ─────────────────────────────────────────────────────────────────────────────

class ConversationListScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var searchQuery by remember { mutableStateOf("") }

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Сообщения", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple {},
                    contentAlignment = Alignment.Center,
                ) { Text("✏️", fontSize = 16.sp) }
            }

            // Поиск
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(44.dp)
                    .clip(RoundedCornerShape(12.dp)).background(NightSurface),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔍", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Поиск диалогов...", fontSize = 14.sp, color = NightTextSecondary)
                }
            }

            // Онлайн-сторис
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                sampleDialogs.filter { it.isOnline }.forEach { d ->
                    OnlineAvatar(d.avatarEmoji, d.name.split(" ").first())
                }
                // Добавить
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(NightSurface)
                            .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("+", fontSize = 22.sp, color = NightTextSecondary) }
                    Text("Найти", fontSize = 10.sp, color = NightTextSecondary)
                }
            }

            // Разделитель
            Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp).background(Color.White.copy(alpha = 0.06f)))

            // Список диалогов
            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(sampleDialogs) { dialog ->
                    DialogRow(dialog, onClick = { nav.push(ConversationScreen(dialog.id, dialog.name, dialog.avatarEmoji)) })
                }
            }
        }
    }
}

@Composable
private fun OnlineAvatar(emoji: String, name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient))
                    .border(2.dp, NightBackground, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 22.sp) }
            Box(Modifier.size(12.dp).clip(CircleShape).background(Soft).border(2.dp, NightBackground, CircleShape))
        }
        Text(name, fontSize = 10.sp, color = NightTextSecondary)
    }
}

@Composable
private fun DialogRow(dialog: DialogItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Аватар
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.size(50.dp).clip(CircleShape)
                    .background(
                        if (dialog.isEvent) Brush.linearGradient(BrandGradient)
                        else Brush.linearGradient(listOf(Violet600.copy(alpha = 0.22f), Violet600.copy(alpha = 0.22f)))
                    ),
                contentAlignment = Alignment.Center,
            ) { Text(dialog.avatarEmoji, fontSize = 22.sp) }
            if (dialog.isOnline) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Soft).border(2.dp, NightBackground, CircleShape))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dialog.name, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(dialog.time, fontSize = 11.sp, color = NightTextTertiary)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dialog.lastMessage, fontSize = 13.sp, color = NightTextSecondary, maxLines = 1,
                    modifier = Modifier.weight(1f))
                if (dialog.unread > 0) {
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).background(Violet600),
                        contentAlignment = Alignment.Center,
                    ) { Text("${dialog.unread}", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.ExtraBold) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ConversationScreen — конкретный диалог
// ─────────────────────────────────────────────────────────────────────────────

private data class ChatMessage(
    val text: String,
    val isOwn: Boolean,
    val time: String,
    val emoji: String? = null,
    val eventPreview: String? = null,
)

private val sampleMessages = listOf(
    ChatMessage("Привет! Ты идёшь на джаз-концерт в пятницу?", false, "20:10"),
    ChatMessage("Да, уже взял билеты! 🎵", true, "20:11"),
    ChatMessage("Классно! Там ещё места есть для ещё одного?", false, "20:12"),
    ChatMessage("Вот ссылка на событие →", true, "20:13", eventPreview = "Jazz Квартал · Алатау Бар · Пт 20:00 · 2 000 ₸"),
    ChatMessage("О, отлично! Я тоже возьму.", false, "20:14"),
    ChatMessage("Тогда встречаемся у входа в 19:45?", true, "20:14"),
    ChatMessage("Договорились! До встречи 🤝", false, "20:14"),
)

data class ConversationScreen(
    val dialogId: String,
    val name: String,
    val avatarEmoji: String,
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var inputText by remember { mutableStateOf("") }
        val listState = rememberLazyListState()

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                    .clickableNoRipple { nav.pop() }, contentAlignment = Alignment.Center) {
                    Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)),
                    contentAlignment = Alignment.Center,
                ) { Text(avatarEmoji, fontSize = 18.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("онлайн", fontSize = 11.sp, color = Soft)
                }
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                    .clickableNoRipple {}, contentAlignment = Alignment.Center) {
                    Text("📞", fontSize = 16.sp)
                }
            }

            // Сообщения
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Дата-разделитель
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("Сегодня", fontSize = 11.sp, color = NightTextSecondary)
                        }
                    }
                }
                items(sampleMessages) { msg ->
                    MessageBubble(msg)
                }
            }

            // Ввод
            Row(
                Modifier.fillMaxWidth().background(NightSurface).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickableNoRipple {},
                    contentAlignment = Alignment.Center,
                ) { Text("😊", fontSize = 18.sp) }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(inputText.ifEmpty { "Сообщение..." }, fontSize = 14.sp, color = if (inputText.isEmpty()) NightTextSecondary else Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)).clickableNoRipple {},
                    contentAlignment = Alignment.Center,
                ) { Text("➤", color = Color.White, fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isOwn) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (msg.isOwn) Alignment.End else Alignment.Start,
        ) {
            if (msg.eventPreview != null) {
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (msg.isOwn) Violet600.copy(alpha = 0.20f) else NightSurface)
                        .border(1.dp, Violet600.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(msg.text, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Violet600.copy(alpha = 0.15f)).padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎵", fontSize = 20.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(msg.eventPreview, fontSize = 12.sp, color = Violet400, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            } else {
                Box(
                    Modifier.clip(RoundedCornerShape(18.dp))
                        .background(
                            if (msg.isOwn) Brush.linearGradient(BrandGradient)
                            else Brush.linearGradient(listOf(NightSurface, NightSurface))
                        ).padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(msg.text, fontSize = 14.sp, color = Color.White, lineHeight = 19.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(msg.time, fontSize = 10.sp, color = NightTextTertiary, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}
