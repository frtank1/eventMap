package com.eventmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.example.ui.ExampleScreen
import com.eventmap.theme.ui_kit.AOButton

// Заглушка экрана авторизации. Сюда App уводит при GlobalError.Unauthorized.
// При появлении реальной auth-фичи заменить на её экран.
class AuthStubScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Требуется авторизация")
            AOButton(
                onClick = { navigator.replaceAll(ExampleScreen()) },
                text = "На главный экран"
            )
        }
    }
}
