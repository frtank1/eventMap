package com.eventmap.auth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.auth.ui.screens.components.AuthTextField
import com.eventmap.auth.ui.screens.components.BrandLogo
import com.eventmap.auth.ui.screens.components.LinkText
import com.eventmap.auth.ui.screens.components.SocialButtons
import com.eventmap.auth.ui.viewmodel.RegisterViewModel
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  A5 — Регистрация
// =====================================================================

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NightBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(54.dp))
            BrandLogo(size = 56, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))

            Text(
                "Создать аккаунт",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Присоединяйся к EventMap",
                fontSize = 14.sp,
                color = NightTextSecondary,
            )

            Spacer(Modifier.height(28.dp))
            AuthTextField(name, { name = it }, "Имя", Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            AuthTextField(email, { email = it }, "Email", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(12.dp))
            AuthTextField(password, { password = it }, "Пароль", Modifier.fillMaxWidth(), isPassword = true)
            Spacer(Modifier.height(12.dp))
            AuthTextField(confirm, { confirm = it }, "Повторите пароль", Modifier.fillMaxWidth(), isPassword = true)

            Spacer(Modifier.height(24.dp))

            // CTA «Зарегистрироваться»
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple { if (!loading) vm.register(name, email, password, confirm) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (loading) "Создаём…" else "Зарегистрироваться",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Spacer(Modifier.height(16.dp))
            SocialButtons(onGoogle = {}, onApple = {}, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text("Уже есть аккаунт? ", color = NightTextSecondary, fontSize = 14.sp)
                LinkText("Войти", onClick = { nav.push(LoginScreen()) })
            }

            SnackbarHost(snackbar)
        }
    }
}
