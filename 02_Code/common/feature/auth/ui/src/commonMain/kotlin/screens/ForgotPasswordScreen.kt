package com.eventmap.auth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.auth.ui.screens.components.AuthTextField
import com.eventmap.auth.ui.screens.components.LinkText
import com.eventmap.auth.ui.viewmodel.ForgotViewModel
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  A7 — Восстановление пароля
// =====================================================================

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NightBackground)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(58.dp))

            // Иконка-кружок
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (sent) Soft.copy(alpha = 0.15f) else Violet600.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (sent) "✉️" else "🔑", fontSize = 30.sp)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                if (sent) "Ссылка отправлена" else "Сброс пароля",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (sent)
                    "Мы отправили ссылку на\n$email\nПроверь почту и следуй инструкции."
                else
                    "Введи свой email — пришлём ссылку для сброса пароля.",
                fontSize = 14.sp,
                color = NightTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(32.dp))

            if (!sent) {
                AuthTextField(
                    email, { email = it }, "Email",
                    Modifier.fillMaxWidth(),
                    keyboardType = KeyboardType.Email,
                )
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(BrandGradient))
                        .clickableNoRipple { if (!loading) vm.submit(email) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (loading) "Отправляем…" else "Отправить ссылку",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            LinkText("← Вернуться ко входу", onClick = { nav.pop() })

            SnackbarHost(snackbar)
        }
    }
}
