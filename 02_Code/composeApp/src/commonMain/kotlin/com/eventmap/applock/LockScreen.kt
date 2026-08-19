package com.eventmap.applock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple
import kotlinx.coroutines.launch

/**
 * Экран разблокировки (оверлей). Показывается при заблокированном App Lock:
 * холодный старт, возврат из фона или смерть токена. На успешном PIN/биометрии
 * [AppLockController.completeUnlock] поднимает сессию (при необходимости — тихий ре-логин).
 *
 * @param onRecovered токен был мёртв и восстановлен ре-логином → увести на главный экран.
 * @param onFullLogin тихий ре-логин невозможен → полный вход по паролю.
 */
@Composable
fun LockScreen(
    controller: AppLockController,
    onRecovered: () -> Unit,
    onFullLogin: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val biometricUsable = remember { controller.isBiometricEnabled() && controller.isBiometricAvailable() }
    val shake = remember { Animatable(0f) }

    suspend fun finishUnlock() {
        busy = true
        val result = controller.completeUnlock()
        busy = false
        when (result) {
            UnlockResult.Recovered -> onRecovered()
            UnlockResult.NeedFullLogin -> onFullLogin()
            UnlockResult.Success -> Unit // state → Unlocked, оверлей снимет App
        }
    }

    fun submit(pin: String) {
        scope.launch {
            busy = true
            if (controller.verifyPin(pin)) {
                finishUnlock()
            } else {
                busy = false
                error = true
                entered = ""
                shake.snapTo(0f)
                shake.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 300
                        (-12f) at 50; (12f) at 100; (-8f) at 150; (8f) at 200; 0f at 300
                    },
                )
            }
        }
    }

    fun onDigit(digit: Int) {
        if (busy) return
        error = false
        if (entered.length < AppLockController.PIN_LENGTH) {
            entered += digit.toString()
            if (entered.length == AppLockController.PIN_LENGTH) submit(entered)
        }
    }

    fun onBackspace() {
        if (busy) return
        error = false
        if (entered.isNotEmpty()) entered = entered.dropLast(1)
    }

    fun triggerBiometric() {
        scope.launch {
            busy = true
            if (controller.authenticateBiometric()) finishUnlock() else busy = false
        }
    }

    // Авто-вызов биометрии при открытии.
    LaunchedEffect(Unit) {
        if (biometricUsable) triggerBiometric()
    }

    Box(
        Modifier.fillMaxSize().background(NightBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🔒", fontSize = 32.sp)
            }

            Spacer(Modifier.height(20.dp))
            Text("С возвращением", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (error) "Неверный PIN-код" else "Введите PIN-код",
                color = if (error) Color(0xFFE24B4A) else NightTextSecondary,
                fontSize = 14.sp,
            )

            Spacer(Modifier.height(28.dp))
            PinDots(
                filled = entered.length,
                total = AppLockController.PIN_LENGTH,
                error = error,
                modifier = Modifier.offset(x = shake.value.dp),
            )

            Spacer(Modifier.height(40.dp))
            PinKeypad(onDigit = ::onDigit, onBackspace = ::onBackspace)

            if (biometricUsable) {
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NightSurface)
                        .clickableNoRipple { triggerBiometric() }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Войти по Face ID / отпечатку", color = Violet600, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Войти по паролю",
                color = NightTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickableNoRipple { onFullLogin() }.padding(8.dp),
            )
        }
    }
}
