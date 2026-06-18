package com.eventmap.auth.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.NightTextTertiary
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  Auth UI компоненты — Aurora Violet, ночная тема
// =====================================================================

/** Бренд-логотип: градиентный кружок с иконкой 📍 */
@Composable
fun BrandLogo(size: Int = 88, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(BrandGradient)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Place,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((size * 0.55).dp),
        )
    }
}

/** Тёмный текстовый инпут по Aurora Violet спеке. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var showPassword by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = if (isPassword) {
            {
                Icon(
                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (showPassword) "Скрыть" else "Показать",
                    tint = NightTextSecondary,
                    modifier = Modifier.clickableNoRipple { showPassword = !showPassword },
                )
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            // Контейнер
            unfocusedContainerColor = NightSurface,
            focusedContainerColor = NightSurface,
            // Текст
            unfocusedTextColor = Color.White,
            focusedTextColor = Color.White,
            // Лейбл
            unfocusedLabelColor = NightTextSecondary,
            focusedLabelColor = Violet400,
            // Бордер
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            focusedBorderColor = Violet600,
            // Курсор
            cursorColor = Violet600,
        ),
        modifier = modifier,
    )
}

/** Кнопки соц-входа Google / Apple — тёмный стиль. */
@Composable
fun SocialButtons(
    onGoogle: () -> Unit,
    onApple: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Разделитель «или»
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.10f)))
        Text(
            "или",
            color = NightTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.10f)))
    }
    Spacer(Modifier.height(12.dp))
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SocialButton("🇬", "Google", onGoogle, Modifier.weight(1f))
        SocialButton("", "Apple", onApple, Modifier.weight(1f))
    }
}

@Composable
private fun SocialButton(emoji: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (emoji.isNotEmpty()) {
                Text(emoji, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
            }
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Текстовая ссылка-кнопка в Violet-цвете. */
@Composable
fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Violet400,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.clickableNoRipple(onClick),
    )
}

/** Dots-пейджер индикатор. */
@Composable
fun DotsIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val active = i == current
            Box(
                Modifier
                    .size(width = if (active) 22.dp else 7.dp, height = 7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (active) Brush.linearGradient(BrandGradient)
                        else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.25f))),
                    ),
            )
        }
    }
}
