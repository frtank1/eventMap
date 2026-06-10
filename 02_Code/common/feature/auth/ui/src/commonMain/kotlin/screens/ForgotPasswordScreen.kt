package com.eventmap.auth.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.auth.ui.screens.components.AuthTextField
import com.eventmap.auth.ui.screens.components.LinkText
import com.eventmap.auth.ui.viewmodel.ForgotViewModel
import com.eventmap.theme.EventMapTheme
import com.eventmap.theme.ui_kit.PrimaryGradientButton

class ForgotPasswordScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<ForgotViewModel>()
        val snackbar = remember { SnackbarHostState() }
        val loading by vm.loading.collectAsState()
        val sent by vm.sent.collectAsState()
        var email by remember { mutableStateOf("") }

        LaunchedEffect(Unit) { vm.errorMessage.collect { snackbar.showSnackbar(it) } }

        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
            Column(
                modifier = Modifier.fillMaxSize().padding(inner).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(40.dp))
                Text("Сброс пароля", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (sent) "Мы отправили ссылку для сброса на $email" else "Введи email — пришлём ссылку для сброса пароля",
                    style = MaterialTheme.typography.bodyLarge,
                    color = EventMapTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))

                if (!sent) {
                    AuthTextField(email, { email = it }, "Email", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email)
                    Spacer(Modifier.height(24.dp))
                    PrimaryGradientButton(text = "Отправить ссылку", loading = loading, onClick = { vm.submit(email) })
                }
                Spacer(Modifier.height(20.dp))
                LinkText("Вернуться ко входу", onClick = { nav.pop() })
            }
        }
    }
}
