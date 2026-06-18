package com.eventmap.settings.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
//  AccountManageScreen  — управление аккаунтом
//  DataExportScreen     — экспорт данных
//  DeleteAccountScreen  — подтверждение удаления аккаунта
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)
private val Danger = Color(0xFFE24B4A)
private val Gold = Color(0xFFFFD700)
private val Coral500 = Color(0xFFFF5C8A)
private val ProGradient = listOf(Gold.copy(alpha = 0.80f), Coral500)

// ─────────────────────────────────────────────────────────────────────────────
//  AccountManageScreen
// ─────────────────────────────────────────────────────────────────────────────

class AccountManageScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Аккаунт", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                // Профиль-карточка
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(NightSurface).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)),
                            contentAlignment = Alignment.Center,
                        ) { Text("🦁", fontSize = 24.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Азамат Темиржан", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Brush.linearGradient(ProGradient)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("Pro", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                            Text("@azamat_t · azamattemirzhan@gmail.com", fontSize = 11.sp, color = NightTextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                AccLabel("ПОДПИСКА")
                Spacer(Modifier.height(8.dp))

                // Pro-план
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Violet600.copy(alpha = 0.18f), Coral500.copy(alpha = 0.08f))))
                        .border(1.dp, Violet600.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickableNoRipple {}.padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✨", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("EventMap Pro · Год", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Следующее списание: 13 июн 2027 · 9 990 ₸", fontSize = 11.sp, color = NightTextSecondary)
                        }
                        Text("›", fontSize = 18.sp, color = NightTextSecondary)
                    }
                }

                Spacer(Modifier.height(6.dp))
                AccRow("Отменить подписку", NightTextSecondary, false) {}

                Spacer(Modifier.height(20.dp))
                AccLabel("БЕЗОПАСНОСТЬ")
                Spacer(Modifier.height(8.dp))
                AccRow("Сменить номер телефона", Color.White, true) {}
                AccRow("Изменить email", Color.White, true) {}
                AccRow("Активные сессии", Color.White, true) {}
                AccRow("Двухфакторная аутентификация", Color.White, true) {}

                Spacer(Modifier.height(20.dp))
                AccLabel("ДАННЫЕ")
                Spacer(Modifier.height(8.dp))
                AccRow("Скачать мои данные", Color.White, true) { nav.push(DataExportScreen()) }
                AccRow("Связанные приложения", Color.White, true) {}
                AccRow("История авторизаций", Color.White, true) {}

                Spacer(Modifier.height(20.dp))
                AccLabel("ОПАСНАЯ ЗОНА")
                Spacer(Modifier.height(8.dp))
                AccRow("Деактивировать аккаунт", Danger.copy(alpha = 0.80f), false) {}
                AccRow("Удалить аккаунт", Danger, false) { nav.push(DeleteAccountScreen()) }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AccLabel(text: String) {
    Text(text, color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
}

@Composable
private fun AccRow(label: String, color: Color, showArrow: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(NightSurface)
            .clickableNoRipple(onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = color, modifier = Modifier.weight(1f))
        if (showArrow) Text("›", fontSize = 18.sp, color = NightTextTertiary)
    }
    Spacer(Modifier.height(6.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
//  DataExportScreen — экспорт данных
// ─────────────────────────────────────────────────────────────────────────────

class DataExportScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var requested by remember { mutableStateOf(false) }

        val dataTypes = listOf(
            "📅" to "История событий" to "91 запись",
            "🎟" to "Купленные билеты" to "14 билетов",
            "💬" to "Сообщения" to "2 340 сообщений",
            "📸" to "Фотографии" to "87 фото",
            "⭐" to "Отзывы" to "12 отзывов",
            "🏅" to "Достижения" to "4 бейджа",
            "👥" to "Подписчики/подписки" to "310 / 127",
            "📊" to "Статистика активности" to "За 2 года",
        )

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Скачать мои данные", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(14.dp),
                ) {
                    Text(
                        "По запросу GDPR/Закона РК о персональных данных мы подготовим архив всех ваших данных. Файл будет отправлен на email в течение 72 часов.",
                        fontSize = 13.sp, color = NightTextSecondary, lineHeight = 19.sp,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("ЧТО БУДЕТ ВКЛЮЧЕНО", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    dataTypes.forEach { (emojiLabel, count) ->
                        val (emoji, label) = emojiLabel
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NightSurface).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(emoji, fontSize = 16.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(label, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
                            Text(count, fontSize = 11.sp, color = NightTextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (!requested) {
                    Box(
                        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(BrandGradient))
                            .clickableNoRipple { requested = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("Запросить архив данных", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                } else {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(Soft.copy(alpha = 0.12f))
                            .border(1.dp, Soft.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✓", fontSize = 24.sp, color = Soft)
                            Spacer(Modifier.height(6.dp))
                            Text("Запрос отправлен!", fontSize = 14.sp, color = Soft, fontWeight = FontWeight.SemiBold)
                            Text("Письмо придёт на azamattemirzhan@gmail.com в течение 72 часов", fontSize = 11.sp, color = NightTextSecondary, textAlign = TextAlign.Center)
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DeleteAccountScreen — подтверждение удаления
// ─────────────────────────────────────────────────────────────────────────────

class DeleteAccountScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var confirmText by remember { mutableStateOf("") }
        val isConfirmed = confirmText.trim().lowercase() == "удалить"

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Удалить аккаунт", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Danger)
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(80.dp).clip(CircleShape)
                            .background(Danger.copy(alpha = 0.12f)).border(2.dp, Danger.copy(alpha = 0.40f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("⚠️", fontSize = 36.sp) }
                }

                Spacer(Modifier.height(20.dp))
                Text("Это действие необратимо", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text(
                    "При удалении аккаунта будут безвозвратно удалены: профиль, история событий, купленные билеты, сообщения, достижения и все личные данные.",
                    fontSize = 13.sp, color = NightTextSecondary, lineHeight = 20.sp, textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))

                // Последствия
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Danger.copy(alpha = 0.08f))
                        .border(1.dp, Danger.copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "Все данные удаляются навсегда",
                        "Активная Pro-подписка не возвращается",
                        "Купленные билеты аннулируются",
                        "Восстановление невозможно",
                    ).forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✕", fontSize = 12.sp, color = Danger, modifier = Modifier.width(20.dp))
                            Text(item, fontSize = 12.sp, color = Color.White.copy(alpha = 0.80f))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("Для подтверждения введите «удалить»:", fontSize = 13.sp, color = NightTextSecondary)
                Spacer(Modifier.height(8.dp))

                // Поле ввода-заглушка
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(NightSurface)
                        .border(1.dp, if (isConfirmed) Danger.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        confirmText.ifEmpty { "Введите текст..." },
                        fontSize = 14.sp, color = if (confirmText.isEmpty()) NightTextTertiary else if (isConfirmed) Danger else Color.White,
                    )
                }

                Spacer(Modifier.height(4.dp))
                // Симуляция — кнопки для теста
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(NightSurface).clickableNoRipple { confirmText = "удалить" }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text("Вставить «удалить»", fontSize = 10.sp, color = NightTextSecondary)
                    }
                    if (confirmText.isNotEmpty()) {
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(NightSurface).clickableNoRipple { confirmText = "" }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                            Text("Очистить", fontSize = 10.sp, color = NightTextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (isConfirmed) Danger else NightSurface)
                        .border(1.dp, if (!isConfirmed) Color.White.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickableNoRipple { if (isConfirmed) nav.popUntilRoot() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Удалить аккаунт навсегда",
                        color = if (isConfirmed) Color.White else NightTextSecondary,
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.07f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("Отмена", color = NightTextSecondary, fontSize = 14.sp) }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
