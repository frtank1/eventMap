package com.eventmap.event.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.event.models.EventDetail
import com.eventmap.event.ui.viewmodel.MyEventsViewModel
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.EventMapIcons
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  Мои события: список созданных мной + правка (D6 префилл) + удаление.
// =====================================================================

class MyEventsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<MyEventsViewModel>()
        val events by vm.events.collectAsState()
        val loading by vm.loading.collectAsState()
        val error by vm.error.collectAsState()
        var deleteCandidate by remember { mutableStateOf<EventDetail?>(null) }

        // перезагрузка при каждом входе (в т.ч. возврате после правки/создания)
        LaunchedEffect(Unit) { vm.load() }

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Мои события", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Brush.linearGradient(BrandGradient))
                        .clickableNoRipple { nav.push(CreateEventScreen()) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(EventMapIcons.Plus, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Создать", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            error?.let {
                Text(it, fontSize = 12.sp, color = Color(0xFFFF7A8A), modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Violet400)
                }

                events.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Ты пока ничего не создавал", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Первое событие появится здесь —\nего можно будет править и удалять",
                            fontSize = 12.sp, color = NightTextSecondary, lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 40.dp))
                    }
                }

                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(events, key = { it.id }) { e ->
                        MyEventRow(
                            event = e,
                            onEdit = { nav.push(CreateEventScreen(editEventId = e.id)) },
                            onDelete = { deleteCandidate = e },
                        )
                    }
                }
            }
        }

        deleteCandidate?.let { candidate ->
            AlertDialog(
                onDismissRequest = { deleteCandidate = null },
                containerColor = NightSurface,
                title = { Text("Удалить событие?", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text("«${candidate.title}» исчезнет с карты и из ленты. Это действие нельзя отменить.", color = NightTextSecondary) },
                confirmButton = {
                    TextButton(onClick = { vm.delete(candidate.id); deleteCandidate = null }) {
                        Text("Удалить", color = Color(0xFFFF7A8A), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteCandidate = null }) { Text("Отмена", color = NightTextSecondary) }
                },
            )
        }
    }
}

@Composable
private fun MyEventRow(event: EventDetail, onEdit: () -> Unit, onDelete: () -> Unit) {
    val (statusLabel, statusColor) = when (event.status) {
        "live" -> "🔴 Live" to Color(0xFFFF4757)
        "ended" -> "✔ Прошло" to NightTextSecondary
        else -> "✓ Скоро" to Soft
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(event.title, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(
                listOfNotNull(
                    event.startAt.take(10).takeIf { it.isNotBlank() },
                    event.location.name.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                fontSize = 11.sp, color = NightTextSecondary, maxLines = 1,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) { Text(statusLabel, fontSize = 9.sp, color = statusColor, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.width(8.dp))
                Icon(
                    EventMapIcons.People,
                    contentDescription = null,
                    tint = NightTextSecondary,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text("${event.goingCount}", fontSize = 10.sp, color = NightTextSecondary)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                .clickableNoRipple(onEdit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                EventMapIcons.Edit,
                contentDescription = "Редактировать",
                tint = Color.White,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFE24B4A).copy(alpha = 0.12f))
                .clickableNoRipple(onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                EventMapIcons.Delete,
                contentDescription = "Удалить",
                tint = Color(0xFFFF7A8A),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
