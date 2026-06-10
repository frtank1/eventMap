package com.eventmap.search.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.event.ui.screens.EventDetailScreen
import com.eventmap.event.ui.screens.OrganizerScreen
import com.eventmap.search.models.SearchHit
import com.eventmap.search.models.SearchType
import com.eventmap.search.ui.viewmodel.SearchViewModel
import com.eventmap.theme.EventMapTheme

class SearchScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<SearchViewModel>()
        val query by vm.query.collectAsState()
        val type by vm.type.collectAsState()
        val results by vm.results.collectAsState()
        val loading by vm.loading.collectAsState()

        Scaffold { inner ->
            Column(Modifier.fillMaxSize().padding(inner).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.clickable { nav.pop() })
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.onQueryChange(it) },
                        placeholder = { Text("Поиск событий, мест, людей") },
                        singleLine = true,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchType.entries) { t ->
                        val sel = t == type
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp))
                                .background(if (sel) EventMapTheme.colors.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { vm.onTypeChange(t) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(t.label, style = MaterialTheme.typography.labelLarge,
                                color = if (sel) EventMapTheme.colors.accent else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    query.isBlank() -> Hint("Начни вводить запрос")
                    results.isEmpty() -> Hint("Ничего не найдено по «$query»")
                    else -> LazyColumn(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(results) { hit ->
                            ResultRow(hit, onClick = {
                                when (hit.type) {
                                    "events" -> hit.eventId?.let { nav.push(EventDetailScreen(it)) }
                                    "organizers" -> nav.push(OrganizerScreen(hit.id))
                                    else -> Unit // людей/жанры подключим позже
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(hit: SearchHit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(hit.emoji.ifBlank { "🔎" }, fontSize = 18.sp)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (hit.subtitle.isNotBlank()) {
                Text(hit.subtitle, style = MaterialTheme.typography.bodySmall, color = EventMapTheme.colors.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = EventMapTheme.colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}
