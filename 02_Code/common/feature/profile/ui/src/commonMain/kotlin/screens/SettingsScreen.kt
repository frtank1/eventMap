package com.eventmap.profile.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.profile.ui.viewmodel.SettingsViewModel
import com.eventmap.theme.Danger
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.NightTextTertiary
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  I1 — Настройки (из H1 Профиль → ⚙)
//  Aurora Violet ночная тема
// =====================================================================

class SettingsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<SettingsViewModel>()
        val s by vm.state.collectAsState()

        Column(
            Modifier
                .fillMaxSize()
                .background(NightBackground)
                .verticalScroll(rememberScrollState()),
        ) {
            // Top bar
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 56.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text("Настройки", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            // ── Уведомления ─────────────────────────────────────────────
            SectionHeader("УВЕДОМЛЕНИЯ")
            SettingsToggle("Push-уведомления", "Новые события и лайвы рядом", s.pushNotifications) { vm.setPush(it) }
            SettingsToggle("Email-уведомления", "Дайджест и сводка недели", s.emailNotifications) { vm.setEmail(it) }

            Spacer(Modifier.height(8.dp))

            // ── Приватность ──────────────────────────────────────────────
            SectionHeader("ПРИВАТНОСТЬ")
            SettingsToggle("Публичный профиль", "Виден всем пользователям", s.publicProfile) { vm.setPublic(it) }
            SettingsToggle("Sharing «сейчас здесь»", "Анонимный пульс города + твоя позиция на карте друзей", s.sharingEnabled) { vm.setSharing(it) }

            Spacer(Modifier.height(8.dp))

            // ── Аккаунт ──────────────────────────────────────────────────
            SectionHeader("АККАУНТ")
            SettingsLink("Сменить пароль") {}
            SettingsLink("Активные сессии") {}
            SettingsLink("Выгрузить мои данные") {}

            Spacer(Modifier.height(8.dp))
            SectionHeader("ОПАСНАЯ ЗОНА")
            SettingsLink("Удалить аккаунт", danger = true) {}

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = NightTextSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsToggle(
    label: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NightSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Violet600,
                uncheckedThumbColor = NightTextTertiary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
            ),
        )
    }
}

@Composable
private fun SettingsLink(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (danger) Danger.copy(alpha = 0.08f) else NightSurface)
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = if (danger) Danger else Color.White, fontWeight = FontWeight.Medium)
        Text("›", color = if (danger) Danger.copy(alpha = 0.6f) else NightTextTertiary, fontSize = 18.sp)
    }
}
