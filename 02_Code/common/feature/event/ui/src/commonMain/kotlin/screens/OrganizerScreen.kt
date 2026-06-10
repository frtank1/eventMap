package com.eventmap.event.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.Organizer
import com.eventmap.event.ui.viewmodel.OrganizerViewModel
import com.eventmap.theme.EventMapTheme
import com.eventmap.theme.ui_kit.PrimaryGradientButton
import com.eventmap.theme.ui_kit.SecondaryButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon

data class OrganizerScreen(val organizerId: String) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<OrganizerViewModel>()
        val organizer by vm.organizer.collectAsState()
        val events by vm.events.collectAsState()
        val loading by vm.loading.collectAsState()

        LaunchedEffect(organizerId) { vm.load(organizerId) }

        Scaffold { inner ->
            val org = organizer
            if (loading || org == null) {
                Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Scaffold
            }

            LazyColumn(Modifier.fillMaxSize().padding(inner)) {
                item { OrgHeader(org, onBack = { nav.pop() }, onSubscribe = { vm.toggleSubscribe() }) }
                item {
                    Text(
                        "События",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
                items(events) { ev ->
                    OrgEventRow(ev, onClick = { nav.push(EventDetailScreen(ev.id)) })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun OrgHeader(org: Organizer, onBack: () -> Unit, onSubscribe: () -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(110.dp).background(Brush.linearGradient(EventMapTheme.colors.brandGradient))) {
            Box(
                Modifier.padding(16.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.25f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("‹", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Brush.linearGradient(EventMapTheme.colors.brandGradient)),
                contentAlignment = Alignment.Center,
            ) { Text(org.name.take(1), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(org.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                if (org.isVerified) { Spacer(Modifier.size(4.dp)); Icon(Icons.Rounded.Verified, contentDescription = "Подтверждён", tint = EventMapTheme.colors.info, modifier = Modifier.size(18.dp)) }
            }
            if (org.bio.isNotBlank()) {
                Text(org.bio, style = MaterialTheme.typography.bodyMedium, color = EventMapTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text("${org.eventsCount} событий · ${org.subscribers} подписчиков", style = MaterialTheme.typography.bodySmall, color = EventMapTheme.colors.textTertiary)
            Spacer(Modifier.height(12.dp))
            if (org.isSubscribed) {
                SecondaryButton(text = "Вы подписаны", onClick = onSubscribe)
            } else {
                PrimaryGradientButton(text = "Подписаться", onClick = onSubscribe)
            }
        }
    }
}

@Composable
private fun OrgEventRow(ev: EventDetail, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ConfirmationNumber, contentDescription = null, tint = EventMapTheme.colors.textSecondary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ev.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text("${ev.category} · ${ev.price.label()}", style = MaterialTheme.typography.bodySmall, color = EventMapTheme.colors.textTertiary)
        }
    }
}
