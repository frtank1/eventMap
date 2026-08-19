package com.eventmap.applock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.eventmap.theme.ui_kit.clickableNoRipple
import kotlinx.coroutines.launch

private enum class SetupStage { Create, Confirm, Biometric }

/**
 * Создание PIN-кода (оверлей). Шаги: ввод → подтверждение → (опц.) включение биометрии.
 *
 * @param onDone PIN создан (и при желании включена биометрия).
 * @param onSkip пользователь отложил настройку.
 */
@Composable
fun PinSetupScreen(
    controller: AppLockController,
    onDone: () -> Unit,
    onSkip: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(SetupStage.Create) }
    var firstPin by remember { mutableStateOf("") }
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    fun handleComplete(pin: String) {
        when (stage) {
            SetupStage.Create -> {
                firstPin = pin
                entered = ""
                stage = SetupStage.Confirm
            }

            SetupStage.Confirm -> {
                if (pin == firstPin) {
                    controller.configurePin(pin)
                    if (controller.isBiometricAvailable()) {
                        entered = ""
                        stage = SetupStage.Biometric
                    } else {
                        onDone()
                    }
                } else {
                    error = true
                    entered = ""
                    firstPin = ""
                    stage = SetupStage.Create
                    scope.launch {
                        shake.snapTo(0f)
                        shake.animateTo(0f, keyframes {
                            durationMillis = 300
                            (-12f) at 50; (12f) at 100; (-8f) at 150; (8f) at 200; 0f at 300
                        })
                    }
                }
            }

            SetupStage.Biometric -> Unit
        }
    }

    fun onDigit(digit: Int) {
        error = false
        if (entered.length < AppLockController.PIN_LENGTH) {
            entered += digit.toString()
            if (entered.length == AppLockController.PIN_LENGTH) handleComplete(entered)
        }
    }

    fun onBackspace() {
        error = false
        if (entered.isNotEmpty()) entered = entered.dropLast(1)
    }

    Box(Modifier.fillMaxSize().background(NightBackground)) {
        // «Позже» — только на шагах ввода
        if (stage != SetupStage.Biometric) {
            Text(
                "Позже",
                color = NightTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 24.dp)
                    .clickableNoRipple { onSkip() }
                    .padding(8.dp),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (stage == SetupStage.Biometric) {
                BiometricStep(
                    onEnable = { controller.enableBiometric(true); onDone() },
                    onDecline = { controller.enableBiometric(false); onDone() },
                )
                return@Column
            }

            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🔐", fontSize = 30.sp)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                if (stage == SetupStage.Create) "Придумайте PIN-код" else "Повторите PIN-код",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (error) "PIN-коды не совпали, попробуйте снова"
                else "Он понадобится для быстрого входа",
                color = if (error) Color(0xFFE24B4A) else NightTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
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
        }
    }
}

@Composable
private fun BiometricStep(
    onEnable: () -> Unit,
    onDecline: () -> Unit,
) {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)),
        contentAlignment = Alignment.Center,
    ) {
        Text("👤", fontSize = 30.sp)
    }
    Spacer(Modifier.height(20.dp))
    Text("Быстрый вход", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
    Spacer(Modifier.height(6.dp))
    Text(
        "Входить по Face ID или отпечатку вместо PIN-кода?",
        color = NightTextSecondary,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(32.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(BrandGradient))
            .clickableNoRipple { onEnable() },
        contentAlignment = Alignment.Center,
    ) {
        Text("Включить", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
    Spacer(Modifier.height(12.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NightSurface)
            .clickableNoRipple { onDecline() },
        contentAlignment = Alignment.Center,
    ) {
        Text("Не сейчас", color = NightTextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
