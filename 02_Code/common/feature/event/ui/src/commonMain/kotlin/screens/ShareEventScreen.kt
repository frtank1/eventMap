package com.eventmap.event.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
//  ShareEventScreen — шаринг события
//  Показывается как bottom-sheet поверх экрана события
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)
private val Coral500 = Color(0xFFFF5C8A)

private data class ShareFriend(val name: String, val emoji: String, val sent: Boolean = false)

private val shareFriends = listOf(
    ShareFriend("Тимур", "😎"),
    ShareFriend("Зарина", "🥰"),
    ShareFriend("Данияр", "🤓"),
    ShareFriend("Арайлым", "😄"),
    ShareFriend("Баглан", "🏆"),
    ShareFriend("Камила", "🎵"),
)

private val shareChannels = listOf(
    Triple("💬", "Сообщение", "messenger"),
    Triple("📲", "WhatsApp", "whatsapp"),
    Triple("📸", "Instagram", "instagram"),
    Triple("✈️", "Telegram", "telegram"),
    Triple("🔗", "Скопировать", "copy"),
    Triple("📧", "Email", "email"),
    Triple("🐦", "Twitter/X", "twitter"),
    Triple("📤", "Ещё", "more"),
)

data class ShareEventScreen(
    val eventId: String,
    val eventTitle: String,
    val eventEmoji: String,
    val eventDate: String = "Пт, 14 июн · 20:00",
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val sentTo = remember { mutableStateOf(setOf<String>()) }
        var linkCopied by remember { mutableStateOf(false) }

        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickableNoRipple { nav.pop() }) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(NightBackground)
                    .clickableNoRipple { /* consume */ },
            ) {
                // Drag handle
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.15f)))
                }

                // Превью события
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                        .clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(BrandGradient)),
                        contentAlignment = Alignment.Center,
                    ) { Text(eventEmoji, fontSize = 22.sp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(eventTitle, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(eventDate, fontSize = 11.sp, color = NightTextSecondary)
                    }
                }

                // Отправить друзьям
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ОТПРАВИТЬ ДРУЗЬЯМ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
                    Text("Найти ещё →", fontSize = 11.sp, color = Violet400, modifier = Modifier.clickableNoRipple {})
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(shareFriends) { friend ->
                        val isSent = friend.name in sentTo.value
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickableNoRipple { sentTo.value = sentTo.value + friend.name },
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    Modifier.size(52.dp).clip(CircleShape)
                                        .background(if (isSent) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                                        .border(2.dp, if (isSent) Violet600 else Color.White.copy(alpha = 0.10f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Text(friend.emoji, fontSize = 22.sp) }
                                if (isSent) {
                                    Box(
                                        Modifier.size(18.dp).clip(CircleShape).background(Soft).border(2.dp, NightBackground, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { Text("✓", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.ExtraBold) }
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(friend.name, fontSize = 10.sp, color = if (isSent) Violet400 else NightTextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Каналы шаринга
                Row(Modifier.padding(start = 16.dp, bottom = 8.dp)) {
                    Text("ПОДЕЛИТЬСЯ В", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                }

                val rows = shareChannels.chunked(4)
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (emoji, label, key) ->
                                val isCopy = key == "copy"
                                val active = isCopy && linkCopied
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                        .background(if (active) Soft.copy(alpha = 0.15f) else NightSurface)
                                        .border(1.dp, if (active) Soft.copy(alpha = 0.40f) else Color.Transparent, RoundedCornerShape(12.dp))
                                        .clickableNoRipple { if (isCopy) linkCopied = true }
                                        .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(if (active) "✓" else emoji, fontSize = 20.sp, color = if (active) Soft else Color.Unspecified)
                                    Spacer(Modifier.height(4.dp))
                                    Text(if (active) "Скопировано" else label, fontSize = 9.sp, color = if (active) Soft else NightTextSecondary, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }

                // Ссылка
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp)).background(NightSurface).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🔗", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("eventmap.kz/e/$eventId", fontSize = 12.sp, color = NightTextSecondary, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(Violet600.copy(alpha = 0.20f))
                            .clickableNoRipple { linkCopied = true }.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) { Text(if (linkCopied) "✓" else "Копировать", fontSize = 11.sp, color = if (linkCopied) Soft else Violet400) }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
