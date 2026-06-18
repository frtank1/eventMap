package com.eventmap.auth.ui.screens

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
import com.eventmap.auth.ui.HomeScreenProvider
import com.eventmap.auth.ui.screens.components.AuthTextField
import com.eventmap.auth.ui.screens.components.BrandLogo
import com.eventmap.auth.ui.screens.components.LinkText
import com.eventmap.auth.ui.screens.components.SocialButtons
import com.eventmap.auth.ui.viewmodel.LoginViewModel
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.ui_kit.clickableNoRipple
import org.koin.mp.KoinPlatform.getKoin

// =====================================================================
//  A6 — Вход
// =====================================================================

class LoginScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<LoginViewModel>()
        val snackbar = remember { SnackbarHostState() }
        val loading by vm.loading.collectAsState()
        val success by vm.success.collectAsState()
        val homeProvider = remember { getKoin().get<HomeScreenProvider>() }

        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }

        LaunchedEffect(Unit) { vm.errorMessage.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(success) { if (success) nav.replaceAll(homeProvider.home()) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NightBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            BrandLogo(size = 64)
            Spacer(Modifier.height(20.dp))

            Text(
                "С возвращением 👋",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Войди в EventMap",
                fontSize = 14.sp,
                color = NightTextSecondary,
            )

            Spacer(Modifier.height(32.dp))
            AuthTextField(email, { email = it }, "Email", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email)
            Spacer(Modifier.height(12.dp))
            AuthTextField(password, { password = it }, "Пароль", Modifier.fillMaxWidth(), isPassword = true)

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                LinkText("Забыли пароль?", onClick = { nav.push(ForgotPasswordScreen()) })
            }

            Spacer(Modifier.height(22.dp))

            // CTA «Войти»
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple { if (!loading) vm.login(email, password) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (loading) "Входим…" else "Войти",
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
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Нет аккаунта? ", color = NightTextSecondary, fontSize = 14.sp)
                LinkText("Создать", onClick = { nav.push(RegisterScreen()) })
            }

            SnackbarHost(snackbar)
        }
    }
}
