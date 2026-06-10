package com.eventmap.event.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.EventStatus
import com.eventmap.event.models.MediaItem
import com.eventmap.event.models.Participant
import com.eventmap.event.ui.viewmodel.EventDetailViewModel
import com.eventmap.theme.EventMapTheme
import com.eventmap.theme.ui_kit.EventStatus as PillStatus
import com.eventmap.theme.ui_kit.PrimaryGradientButton
import com.eventmap.theme.ui_kit.SecondaryButton
import com.eventmap.theme.ui_kit.StatusPill
import com.eventmap.theme.ui_kit.clickableNoRipple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon

data class EventDetailScreen(val eventId: String) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<EventDetailViewModel>()
        val snackbar = remember { SnackbarHostState() }
        val event by vm.event.collectAsState()
        val loading by vm.loading.collectAsState()
        val participants by vm.participants.collectAsState()
        val media by vm.media.collectAsState()
        val attended by vm.attended.collectAsState()
        val ticketStatus by vm.ticketStatus.collectAsState()
        val purchasing by vm.purchasing.collectAsState()
        val ratingSent by vm.ratingSent.collectAsState()
        var tab by remember { mutableStateOf(0) }
        var showRating by remember { mutableStateOf(false) }

        LaunchedEffect(eventId) { vm.load(eventId) }
        LaunchedEffect(Unit) { vm.errorMessage.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(ratingSent) { if (ratingSent) showRating = false }

        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
            val ev = event
            if (loading || ev == null) {
                Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Scaffold
            }

            Column(Modifier.fillMaxSize().padding(inner)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Header(ev, onBack = { nav.pop() })
                    Spacer(Modifier.height(16.dp))
                    TabRow(tab, onSelect = { tab = it }, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        when (tab) {
                            0 -> AboutTab(ev, onOpenOrganizer = { id -> nav.push(OrganizerScreen(id)) })
                            1 -> GalleryTab(media)
                            else -> ParticipantsTab(participants)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                Box(Modifier.padding(16.dp)) {
                    when {
                        ev.statusEnum() == EventStatus.Ended ->
                            PrimaryGradientButton(text = if (ratingSent) "Спасибо за оценку ✓" else "Оценить событие", onClick = { showRating = true })
                        ev.statusEnum() == EventStatus.Live ->
                            PrimaryGradientButton(text = if (attended) "Вы здесь ✓" else "Я тоже здесь", onClick = { vm.attend() })
                        !ev.price.isFree ->
                            PrimaryGradientButton(
                                text = when (ticketStatus) { "paid" -> "Билет оформлен ✓"; "pending" -> "Ожидает оплаты…"; else -> "Купить билет · ${ev.price.label()}" },
                                loading = purchasing,
                                onClick = { if (ticketStatus != "paid") vm.purchase() },
                            )
                        else ->
                            PrimaryGradientButton(text = if (attended) "Вы идёте ✓" else "Пойду", onClick = { vm.attend() })
                    }
                }
            }

            if (showRating) {
                ModalBottomSheet(onDismissRequest = { showRating = false }, sheetState = rememberModalBottomSheetState()) {
                    RatingSheet(onSubmit = { value, review -> vm.submitRating(value, review) })
                }
            }
        }
    }
}

@Composable
private fun RatingSheet(onSubmit: (Int, String) -> Unit) {
    var stars by remember { mutableStateOf(0) }
    var review by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        Text("Оцените событие", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        Row {
            (1..5).forEach { i ->
                Text(
                    if (i <= stars) "★" else "☆",
                    fontSize = 34.sp,
                    color = if (i <= stars) EventMapTheme.colors.warning else EventMapTheme.colors.textTertiary,
                    modifier = Modifier.clickableNoRipple { stars = i }.padding(end = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = review, onValueChange = { review = it },
            label = { Text("Отзыв (необязательно)") },
            modifier = Modifier.fillMaxWidth().height(110.dp),
        )
        Spacer(Modifier.height(16.dp))
        PrimaryGradientButton(text = "Отправить", enabled = stars > 0, onClick = { onSubmit(stars, review.trim()) })
    }
}

@Composable
private fun Header(ev: EventDetail, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Brush.linearGradient(EventMapTheme.colors.brandGradient)),
    ) {
        Box(
            Modifier.padding(16.dp).size(40.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.25f)).clickableNoRipple(onBack),
            contentAlignment = Alignment.Center,
        ) { Text("‹", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }

        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            StatusPill(ev.statusEnum().toPill())
            Spacer(Modifier.height(8.dp))
            Text(ev.title, style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
            Text(ev.category, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun TabRow(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tabs = listOf("О событии", "Галерея", "Участники")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEachIndexed { i, t ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                    .background(if (sel) EventMapTheme.colors.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                    .clickableNoRipple { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(t, style = MaterialTheme.typography.labelLarge, color = if (sel) EventMapTheme.colors.accent else MaterialTheme.colorScheme.onSurface, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun AboutTab(ev: EventDetail, onOpenOrganizer: (String) -> Unit) {
    Column {
        Text(ev.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(16.dp))
        InfoRow("📍", ev.location.name)
        InfoRow("🎟️", ev.price.label())
        InfoRow("👥", "${ev.goingCount} идут")
        if (ev.ratingCount > 0) InfoRow("⭐", "${ev.ratingAvg} (${ev.ratingCount} оценок)")
        if (ev.organizerName.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickableNoRipple { if (ev.organizerId.isNotBlank()) onOpenOrganizer(ev.organizerId) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = EventMapTheme.colors.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Организатор", style = MaterialTheme.typography.bodySmall, color = EventMapTheme.colors.textTertiary)
                    Text(ev.organizerName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Text("›", color = EventMapTheme.colors.textTertiary, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun InfoRow(icon: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 15.sp)
        Spacer(Modifier.size(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun GalleryTab(media: List<MediaItem>) {
    if (media.isEmpty()) {
        EmptyState("Пока нет медиа", "Будь первым — добавь фото или видео")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.height(320.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(media) { m ->
            Box(
                Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Text(if (m.type == "video") "▶" else "🖼", fontSize = 22.sp) }
        }
    }
}

@Composable
private fun ParticipantsTab(participants: List<Participant>) {
    if (participants.isEmpty()) {
        EmptyState("Пока никто не идёт", "Стань первым участником")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        participants.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(EventMapTheme.colors.brandGradient)),
                    contentAlignment = Alignment.Center,
                ) { Text(p.name.take(1), color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.size(12.dp))
                Text(p.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = EventMapTheme.colors.textSecondary)
    }
}

private fun EventStatus.toPill(): PillStatus = when (this) {
    EventStatus.Live -> PillStatus.Live
    EventStatus.Soon -> PillStatus.Soon
    EventStatus.Ended -> PillStatus.Was
}
