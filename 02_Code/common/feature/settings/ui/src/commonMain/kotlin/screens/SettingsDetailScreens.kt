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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.Danger
import com.eventmap.theme.Info
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  I2 · Приватность
//  I3 · Сессии
//  I4 · Уведомления по каналам
//  I5 · Внешний вид / Язык
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)

// ─── Shared components ───────────────────────────────────────────────

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple(onBack),
            contentAlignment = Alignment.Center,
        ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text, color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(label: String, sub: String? = null, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked, onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Violet600,
                uncheckedThumbColor = NightTextSecondary, uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
            ),
        )
    }
}

@Composable
private fun NavRow(label: String, sub: String? = null, emoji: String? = null, onClick: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp)).background(NightSurface).clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emoji != null) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        Text("›", color = NightTextTertiary, fontSize = 18.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  I2: Приватность
// ─────────────────────────────────────────────────────────────────────────────

class PrivacySettingsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var profilePublic by remember { mutableStateOf(true) }
        var showOnMap by remember { mutableStateOf(false) }
        var onlineVisible by remember { mutableStateOf(true) }
        var friendsCanTag by remember { mutableStateOf(true) }
        var dataForAds by remember { mutableStateOf(false) }
        var analyticsEnabled by remember { mutableStateOf(true) }

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            SettingsTopBar("Приватность") { nav.pop() }

            SectionHeader("ПРОФИЛЬ")
            SwitchRow("Публичный профиль", "Виден всем в поиске", profilePublic) { profilePublic = !profilePublic }
            SwitchRow("Показывать на карте", "Твой аватар рядом с событиями", showOnMap) { showOnMap = !showOnMap }
            SwitchRow("Статус онлайн", "Друзья видят, когда ты активен", onlineVisible) { onlineVisible = !onlineVisible }

            SectionHeader("КОНТЕНТ")
            SwitchRow("Отметки от друзей", "Разрешить отмечать меня на фото", friendsCanTag) { friendsCanTag = !friendsCanTag }
            NavRow("Закрытые события", "Кто видит приватные события") {}
            NavRow("Заблокированные", "3 пользователя") {}

            SectionHeader("ДАННЫЕ")
            SwitchRow("Персонализация рекламы", "Использовать интересы для рекламы", dataForAds) { dataForAds = !dataForAds }
            SwitchRow("Аналитика использования", "Помочь улучшить приложение", analyticsEnabled) { analyticsEnabled = !analyticsEnabled }
            NavRow("Скачать мои данные", "GDPR / CCPA — архив за 3 дня") {}

            SectionHeader("ОПАСНАЯ ЗОНА")
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Danger.copy(alpha = 0.08f))
                    .border(1.dp, Danger.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .clickableNoRipple {},
                contentAlignment = Alignment.Center,
            ) { Text("Удалить аккаунт", color = Danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  I3: Активные сессии
// ─────────────────────────────────────────────────────────────────────────────

private data class SessionItem(
    val device: String,
    val os: String,
    val location: String,
    val lastSeen: String,
    val isCurrent: Boolean = false,
)

class SessionsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val sessions = remember {
            listOf(
                SessionItem("iPhone 15 Pro", "iOS 17.4", "Алматы, KZ", "Сейчас", isCurrent = true),
                SessionItem("MacBook Pro", "macOS 14.4", "Алматы, KZ", "2 ч. назад"),
                SessionItem("iPad Air", "iPadOS 17.2", "Нур-Султан, KZ", "5 дней назад"),
                SessionItem("Samsung Galaxy S24", "Android 14", "Москва, RU", "14 дней назад"),
            )
        }

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            SettingsTopBar("Сессии") { nav.pop() }

            // Выйти везде
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Danger.copy(alpha = 0.08f))
                    .border(1.dp, Danger.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .clickableNoRipple {},
                contentAlignment = Alignment.Center,
            ) { Text("Выйти на всех устройствах", color = Danger, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }

            SectionHeader("УСТРОЙСТВА (${sessions.size})")

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sessions.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (s.isCurrent) Violet600.copy(alpha = 0.10f) else NightSurface)
                            .border(1.dp, if (s.isCurrent) Violet600.copy(alpha = 0.30f) else Color.Transparent, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Иконка
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val icon = when {
                                s.os.startsWith("iOS") || s.os.startsWith("iPadOS") -> "📱"
                                s.os.startsWith("macOS") -> "💻"
                                else -> "📱"
                            }
                            Text(icon, fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.device, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                if (s.isCurrent) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        Modifier.clip(RoundedCornerShape(999.dp)).background(Soft.copy(alpha = 0.15f))
                                            .padding(horizontal = 7.dp, vertical = 2.dp),
                                    ) { Text("текущее", fontSize = 10.sp, color = Soft) }
                                }
                            }
                            Text("${s.os} · ${s.location}", fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
                            Text(s.lastSeen, fontSize = 11.sp, color = NightTextTertiary, modifier = Modifier.padding(top = 1.dp))
                        }
                        if (!s.isCurrent) {
                            Text("Завершить", fontSize = 12.sp, color = Danger, fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  I4: Уведомления по каналам
// ─────────────────────────────────────────────────────────────────────────────

private data class NotifChannel(
    val emoji: String,
    val label: String,
    val sub: String,
    val push: Boolean = true,
    val email: Boolean = false,
    val inApp: Boolean = true,
)

class NotificationsSettingsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

        val channels = remember {
            mutableStateOf(
                listOf(
                    NotifChannel("🎉", "События рядом", "Новые события в твоём радиусе", push = true, email = false, inApp = true),
                    NotifChannel("❤️", "Реакции и лайки", "Кто оценил твои видео", push = true, email = false, inApp = true),
                    NotifChannel("💬", "Комментарии", "Ответы и упоминания", push = true, email = false, inApp = true),
                    NotifChannel("👥", "Друзья", "Запросы и активность", push = true, email = true, inApp = true),
                    NotifChannel("🎮", "Игра и квесты", "XP, бейджи, позиции в топе", push = false, email = false, inApp = true),
                    NotifChannel("💰", "Платежи", "Чеки и статус билетов", push = true, email = true, inApp = true),
                    NotifChannel("📢", "Бизнес", "Статус заявок, новые клиенты", push = true, email = true, inApp = true),
                    NotifChannel("🔔", "Системные", "Безопасность, обновления", push = true, email = true, inApp = true),
                ),
            )
        }

        var quietFrom by remember { mutableStateOf("23:00") }
        var quietTo by remember { mutableStateOf("08:00") }
        var quietEnabled by remember { mutableStateOf(true) }

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            SettingsTopBar("Уведомления") { nav.pop() }

            // Тихий режим
            SectionHeader("ТИХИЙ ЧАС")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Не беспокоить", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    Text("$quietFrom — $quietTo", fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
                }
                Switch(
                    checked = quietEnabled, onCheckedChange = { quietEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White, checkedTrackColor = Violet600,
                        uncheckedThumbColor = NightTextSecondary, uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                    ),
                )
            }

            SectionHeader("КАНАЛЫ")

            // Заголовок колонок
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                listOf("Push", "Email", "В прил.").forEach { label ->
                    Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 10.sp, color = NightTextTertiary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                channels.value.forEachIndexed { i, ch ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ch.emoji, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ch.label, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(ch.sub, fontSize = 10.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 1.dp))
                        }
                        // Push toggle
                        ChannelToggle(ch.push) { channels.value = channels.value.toMutableList().also { list -> list[i] = ch.copy(push = !ch.push) } }
                        // Email toggle
                        ChannelToggle(ch.email) { channels.value = channels.value.toMutableList().also { list -> list[i] = ch.copy(email = !ch.email) } }
                        // In-app toggle
                        ChannelToggle(ch.inApp) { channels.value = channels.value.toMutableList().also { list -> list[i] = ch.copy(inApp = !ch.inApp) } }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ChannelToggle(active: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier.width(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (active) Violet600 else Color.White.copy(alpha = 0.08f))
                .border(1.dp, if (active) Violet600.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f), CircleShape)
                .clickableNoRipple(onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (active) Text("✓", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  I5: Внешний вид и язык
// ─────────────────────────────────────────────────────────────────────────────

class AppearanceScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selectedTheme by remember { mutableStateOf(0) }  // 0=Dark, 1=Auto
        var selectedLang by remember { mutableStateOf("ru") }
        var compactFeed by remember { mutableStateOf(false) }
        var reducedMotion by remember { mutableStateOf(false) }
        var haptics by remember { mutableStateOf(true) }

        val themes = listOf("🌙" to "Тёмная", "⚙️" to "Авто")
        val langs = listOf("ru" to "Русский 🇷🇺", "kk" to "Қазақша 🇰🇿", "en" to "English 🇺🇸")

        Column(Modifier.fillMaxSize().background(NightBackground).verticalScroll(rememberScrollState())) {
            SettingsTopBar("Внешний вид") { nav.pop() }

            SectionHeader("ТЕМА")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                themes.forEachIndexed { i, (emoji, label) ->
                    val sel = i == selectedTheme
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (sel) Violet600.copy(alpha = 0.18f) else NightSurface)
                            .border(1.dp, if (sel) Violet600.copy(alpha = 0.50f) else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickableNoRipple { selectedTheme = i }.padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(emoji, fontSize = 28.sp)
                        Text(label, fontSize = 13.sp, color = if (sel) Violet400 else NightTextSecondary,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            SectionHeader("ЯЗЫК")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                langs.forEach { (code, label) ->
                    val sel = code == selectedLang
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (sel) Violet600.copy(alpha = 0.12f) else NightSurface)
                            .border(1.dp, if (sel) Violet600.copy(alpha = 0.40f) else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickableNoRipple { selectedLang = code }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
                        if (sel) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(Violet600), contentAlignment = Alignment.Center) {
                                Text("✓", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            SectionHeader("ИНТЕРФЕЙС")
            SwitchRow("Компактная лента", "Меньше отступы между карточками", compactFeed) { compactFeed = !compactFeed }
            SwitchRow("Уменьшить анимации", "Для снижения нагрузки и батареи", reducedMotion) { reducedMotion = !reducedMotion }
            SwitchRow("Тактильный отклик", "Вибрация при нажатиях", haptics) { haptics = !haptics }

            SectionHeader("О ПРИЛОЖЕНИИ")
            NavRow("Версия", sub = "2.4.1 (build 340)") {}
            NavRow("Что нового", emoji = "🎉") {}
            NavRow("Политика конфиденциальности", emoji = "🔒") {}
            NavRow("Условия использования", emoji = "📄") {}

            Spacer(Modifier.height(40.dp))
        }
    }
}
