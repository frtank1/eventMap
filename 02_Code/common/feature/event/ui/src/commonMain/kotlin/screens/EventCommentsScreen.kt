package com.eventmap.event.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.event.ui.viewmodel.CommentsViewModel
import com.eventmap.theme.*

/** Reuses the existing event discussion, not a fabricated separate reel thread. */
data class EventCommentsScreen(val eventId: String) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<CommentsViewModel>()
        LaunchedEffect(eventId) { vm.load(eventId) }
        Column(Modifier.fillMaxSize().background(NightBackground).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.pop() }) { Icon(EventMapIcons.Back, "Назад", tint = NightTextPrimary) }
                Text("Обсуждение события", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = NightTextPrimary)
            }
            CommentsSheet(vm, onDismiss = { nav.pop() }, modifier = Modifier.weight(1f), heightFraction = 1f)
        }
    }
}
