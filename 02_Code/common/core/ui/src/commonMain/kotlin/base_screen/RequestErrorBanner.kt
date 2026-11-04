package com.eventmap.base_screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A request failure is distinct from a successfully loaded empty list. */
@Composable
fun RequestErrorBanner(model: BaseScreenModel, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val message by model.errorText.collectAsState()
    message?.let {
        Column(modifier.fillMaxWidth().background(Color(0xFF45252C)).padding(12.dp)) {
            Text(it, color = Color.White)
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Повторить", color = Color.White) }
            else TextButton(onClick = model::clearError) { Text("Закрыть", color = Color.White) }
        }
    }
}
