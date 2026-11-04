package com.eventmap.search.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.eventmap.event.ui.screens.EventReelScreen
import com.eventmap.search.models.SearchHit
import com.eventmap.search.models.SearchType
import com.eventmap.search.ui.viewmodel.SearchViewModel
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.NightTextTertiary
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  G1–G4 · Поиск событий (Aurora Violet, ночная тема)
// =====================================================================

private val quickCategories = listOf(
    "🎵" to "Концерты",
    "⚽" to "Спорт",
    "🎨" to "Арт",
    "🍕" to "Еда",
    "🧩" to "Квизы",
    "🧘" to "Йога",
    "🎭" to "Театр",
    "🎮" to "Игры",
)

class SearchScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<SearchViewModel>()
        val query by vm.query.collectAsState()
        val type by vm.type.collectAsState()
        val results by vm.results.collectAsState()
        val loading by vm.loading.collectAsState()
        val error by vm.errorText.collectAsState()

        Column(
            Modifier
                .fillMaxSize()
                .background(NightBackground),
        ) {
            com.eventmap.base_screen.RequestErrorBanner(vm, onRetry = { vm.onQueryChange(query) })
            // ── Поисковая строка ──────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.onQueryChange(it) },
                    placeholder = { Text("Поиск событий, мест, людей…", color = NightTextSecondary) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = NightTextSecondary, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = NightSurface,
                        focusedContainerColor = NightSurface,
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                        focusedBorderColor = Violet600,
                        cursorColor = Violet600,
                    ),
                )
                if (query.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickableNoRipple { vm.onQueryChange("") }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    ) {
                        Text("✕", color = NightTextSecondary, fontSize = 14.sp)
                    }
                }
            }

            // ── Тип-фильтры (G3) ─────────────────────────────────────
            LazyRow(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(SearchType.entries) { t ->
                    val sel = t == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (sel) Brush.linearGradient(BrandGradient)
                                else Brush.linearGradient(listOf(NightSurface, NightSurface)),
                            )
                            .clickableNoRipple { vm.onTypeChange(t) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            t.label,
                            color = if (sel) Color.White else NightTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            // ── Результаты / Пустое / Категории ──────────────────────
            when {
                error != null -> Unit
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Violet400, strokeWidth = 2.dp)
                }
                query.isBlank() -> QuickCategoryGrid()
                results.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("🔍", fontSize = 44.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text("Ничего не найдено по «$query»", color = NightTextSecondary, fontSize = 14.sp)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { it.id }) { hit ->
                        ResultRow(hit, onClick = {
                            when (hit.type) {
                                "events" -> hit.eventId?.let { id ->
                                    nav.push(EventReelScreen(listOf(EventDetail(id = id, title = hit.title))))
                                }
                                else -> Unit
                            }
                        })
                    }
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Быстрые категории (G2 — когда строка пуста)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuickCategoryGrid() {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "ПОПУЛЯРНЫЕ КАТЕГОРИИ",
            color = NightTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        // 4 колонки, 2 строки
        val rows = quickCategories.chunked(4)
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { (emoji, label) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(NightSurface)
                            .clickableNoRipple { }
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(emoji, fontSize = 22.sp)
                        Text(label, fontSize = 11.sp, color = NightTextSecondary, textAlign = TextAlign.Center)
                    }
                }
                // заполнение неполной строки
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Строка результата
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultRow(hit: SearchHit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NightSurface)
            .clickableNoRipple(onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Violet600.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(hit.emoji.ifBlank { "🔎" }, fontSize = 20.sp)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.title, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (hit.subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(hit.subtitle, fontSize = 12.sp, color = NightTextTertiary, maxLines = 1)
            }
        }
        Text("›", color = NightTextTertiary, fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
    }
}
