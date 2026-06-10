package com.eventmap.profile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.profile.ui.viewmodel.SettingsViewModel
import com.eventmap.theme.EventMapTheme

class SettingsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<SettingsViewModel>()
        val s by vm.state.collectAsState()

        Scaffold { inner ->
            Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.clickable { nav.pop() })
                    Spacer(Modifier.size(12.dp))
                    Text("Настройки", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                }

                Section("Уведомления")
                ToggleRow("Push-уведомления", s.pushNotifications) { vm.setPush(it) }
                ToggleRow("Email-уведомления", s.emailNotifications) { vm.setEmail(it) }

                Section("Приватность")
                ToggleRow("Публичный профиль", s.publicProfile) { vm.setPublic(it) }
                ToggleRow("Sharing «сейчас здесь»", s.sharingEnabled) { vm.setSharing(it) }

                Section("Аккаунт")
                LinkRow("Сменить пароль") {}
                LinkRow("Активные сессии") {}
                LinkRow("Выгрузить мои данные") {}
                LinkRow("Удалить аккаунт", danger = true) {}
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = EventMapTheme.colors.textSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LinkRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (danger) EventMapTheme.colors.danger else MaterialTheme.colorScheme.onBackground)
        Text("›", color = EventMapTheme.colors.textTertiary, fontSize = 20.sp)
    }
}
