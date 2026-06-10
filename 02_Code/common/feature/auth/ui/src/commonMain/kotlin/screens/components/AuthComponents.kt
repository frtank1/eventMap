package com.eventmap.auth.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.eventmap.theme.EventMapTheme
import com.eventmap.theme.ui_kit.SecondaryButton
import com.eventmap.theme.ui_kit.clickableNoRipple

/** Бренд-логотип: градиентный кружок с пином. */
@Composable
fun BrandLogo(size: Int = 88, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(EventMapTheme.colors.brandGradient)),
        contentAlignment = Alignment.Center,
    ) {
        Text("◎", color = Color.White, fontSize = (size * 0.5).sp, fontWeight = FontWeight.Bold)
    }
}

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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(),
        modifier = modifier,
    )
}

/** Кнопки соц-входа Google / Apple (заглушки под OAuth). */
@Composable
fun SocialButtons(
    onGoogle: () -> Unit,
    onApple: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton(text = "Google", onClick = onGoogle, modifier = Modifier.weight(1f))
        SecondaryButton(text = "Apple", onClick = onApple, modifier = Modifier.weight(1f))
    }
}

/** Текстовая ссылка-кнопка бренд-цветом. */
@Composable
fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = EventMapTheme.colors.accent,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.clickableNoRipple(onClick),
    )
}

@Composable
fun DotsIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            val active = i == current
            Box(
                Modifier
                    .size(if (active) 22.dp else 8.dp, 8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (active) EventMapTheme.colors.accent
                        else EventMapTheme.colors.textTertiary,
                    ),
            )
            Spacer(Modifier.size(0.dp))
        }
    }
}
