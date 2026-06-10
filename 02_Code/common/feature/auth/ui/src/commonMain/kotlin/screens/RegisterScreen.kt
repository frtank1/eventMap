package com.eventmap.auth.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.auth.ui.screens.components.AuthTextField
import com.eventmap.auth.ui.screens.components.LinkText
import com.eventmap.auth.ui.screens.components.SocialButtons
import com.eventmap.auth.ui.viewmodel.RegisterViewModel
import com.eventmap.theme.EventMapTheme
import com.eventmap.theme.ui_kit.PrimaryGradientButton

class RegisterScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<RegisterViewModel>()
        val snackbar = remember { SnackbarHostState() }
        val loading by vm.loading.collectAsState()
        val success by vm.success.collectAsState()

        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }

        LaunchedEffect(Unit) { vm.errorMessage.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(success) { if (success) nav.replaceAll(InterestsScreen()) }

        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Spacer(Modifier.height(16.dp))
                Text("Создать аккаунт", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Text("Присоединяйся к EventMap", style = MaterialTheme.typography.bodyLarge, color = EventMapTheme.colors.textSecondary)
                Spacer(Modifier.height(28.dp))

                AuthTextField(name, { name = it }, "Имя", Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                AuthTextField(email, { email = it }, "Email", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email)
                Spacer(Modifier.height(12.dp))
                AuthTextField(password, { password = it }, "Пароль", Modifier.fillMaxWidth(), isPassword = true)
                Spacer(Modifier.height(12.dp))
                AuthTextField(confirm, { confirm = it }, "Повторите пароль", Modifier.fillMaxWidth(), isPassword = true)

                Spacer(Modifier.height(24.dp))
                PrimaryGradientButton(text = "Зарегистрироваться", loading = loading, onClick = { vm.register(name, email, password, confirm) })
                Spacer(Modifier.height(16.dp))
                SocialButtons(onGoogle = {}, onApple = {}, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text("Уже есть аккаунт? ", color = EventMapTheme.colors.textSecondary)
                    LinkText("Войти", onClick = { nav.push(LoginScreen()) })
                }
            }
        }
    }
}
