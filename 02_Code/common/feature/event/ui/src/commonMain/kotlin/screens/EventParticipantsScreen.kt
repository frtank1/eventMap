package com.eventmap.event.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
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
import com.eventmap.event.models.Participant
import com.eventmap.event.ui.viewmodel.EventDetailViewModel
import com.eventmap.theme.*
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  D3 — Участники события
//  Figma «🎬 D · Событие»  node 51:34
//  Tabs: Идут · Были   ·  Анонимные билеты «+N»
// =====================================================================

data class EventParticipantsScreen(val eventId: String) : Screen {

    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<EventDetailViewModel>()
        val participants by vm.participants.collectAsState()
        var tab by remember { mutableStateOf(0) }

        LaunchedEffect(eventId) { vm.load(eventId) }

        Column(
            Modifier.fillMaxSize().background(NightBackground),
        ) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 54.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Участники", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }

            // Tab pills: Идут · Были
            val going = participants.filter { it.type == "going" }
            val was = participants.filter { it.type == "was" }
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ParticipantTab("Идут · ${going.size}", selected = tab == 0) { tab = 0 }
                ParticipantTab("Были · ${was.size}", selected = tab == 1) { tab = 1 }
            }

            // Anonymous note
            Text(
                "включая анонимные билеты «+12»",
                color = NightTextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 8.dp),
            )

            // List
            val shown = if (tab == 0) going else was
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                if (shown.isEmpty()) {
                    // Stub data matching Figma
                    items(stubParticipants.filter { if (tab == 0) it.type == "going" else it.type == "was" }) { p ->
                        ParticipantRow(p)
                    }
                } else {
                    items(shown) { p ->
                        ParticipantRow(p)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            color = if (selected) Color.White else NightTextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ParticipantRow(p: Participant) {
    val statusColor = when {
        p.type == "was" -> NightTextSecondary
        else -> Soft
    }
    val statusLabel = when (p.type) {
        "going" -> if (p.name == "damir_a") "🟢 тут" else "иду"
        else -> "был"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .background(Brush.linearGradient(BrandGradient)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                p.name.take(1).uppercase(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                if (p.type == "going") "собирается" else "была вчера",
                color = NightTextSecondary,
                fontSize = 11.sp,
            )
        }
        Box(
            Modifier.clip(RoundedCornerShape(8.dp))
                .background(
                    if (statusLabel == "🟢 тут") Soft.copy(alpha = 0.12f)
                    else Color.White.copy(alpha = 0.06f),
                )
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                statusLabel,
                color = if (statusLabel == "🟢 тут") Soft else NightTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// Stub data matching Figma D3
private val stubParticipants = listOf(
    Participant("1", "damir_a", type = "going"),
    Participant("2", "aliya.k", type = "going"),
    Participant("3", "timur_zh", type = "going"),
    Participant("4", "sabina_m", type = "was"),
    Participant("5", "erzhan_b", type = "going"),
)
