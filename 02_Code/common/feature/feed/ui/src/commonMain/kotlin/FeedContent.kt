package com.eventmap.feed.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmap.feed.models.FeedItem
import com.eventmap.feed.ui.viewmodel.FeedViewModel
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.NightTextTertiary
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple
import org.koin.mp.KoinPlatform.getKoin

// =====================================================================
//  F1–F4 · Лента подписок (Aurora Violet, ночная тема)
// =====================================================================

private val feedCategories = listOf("Все", "Сегодня", "Концерты", "Спорт", "Арт", "Еда", "Квизы")

@Composable
fun FeedContent(
    modifier: Modifier = Modifier,
    onOpenEvent: (String) -> Unit = {},
    onOpenSubscriptions: () -> Unit = {},
) {
    val vm = remember { getKoin().get<FeedViewModel>() }
    val items by vm.items.collectAsState()
    val loading by vm.loading.collectAsState()

    Column(
        modifier
            .fillMaxSize()
            .background(NightBackground),
    ) {
        // ── Шапка ────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 56.dp, start = 20.dp, end = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Лента", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple { onOpenSubscriptions() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("👥", fontSize = 16.sp)
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple { vm.load() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↻", color = NightTextSecondary, fontSize = 16.sp)
                }
            }
        }

        // ── Chip-фильтры категорий (F2) ───────────────────────────────
        FeedCategoryChips()

        // ── Контент ───────────────────────────────────────────────────
        com.eventmap.base_screen.RequestErrorBanner(vm, onRetry = vm::load)
        val error by vm.errorText.collectAsState()
        when {
            error != null && items.isEmpty() -> Unit
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Violet400, strokeWidth = 2.dp)
            }
            items.isEmpty() -> EmptyFeed()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    FeedCard(item = item, onClick = { onOpenEvent(item.eventId) })
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Chip-фильтры категорий
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FeedCategoryChips() {
    var selected by remember { mutableStateOf(0) }
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        itemsIndexed(feedCategories) { i, label ->
            val active = i == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (active) Brush.linearGradient(BrandGradient)
                        else Brush.linearGradient(listOf(NightSurface, NightSurface)),
                    )
                    .clickableNoRipple { selected = i }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    color = if (active) Color.White else NightTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Карточка события (F3)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FeedCard(item: FeedItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Иконка категории
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Violet600.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(categoryEmoji(item.category), fontSize = 22.sp)
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.organizerName.isNotBlank()) {
                    Text(item.organizerName, fontSize = 12.sp, color = Violet400, fontWeight = FontWeight.Medium)
                    Text(" · ", fontSize = 12.sp, color = NightTextTertiary)
                }
                Text(item.timeLabel, fontSize = 12.sp, color = NightTextSecondary)
            }
            if (item.category.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(item.category, fontSize = 10.sp, color = NightTextSecondary, fontWeight = FontWeight.Medium)
                }
            }
        }

        Text("›", color = NightTextTertiary, fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
    }

    Box(Modifier.fillMaxWidth().height(1.dp).padding(start = 82.dp).background(Color.White.copy(alpha = 0.05f)))
}

// ─────────────────────────────────────────────────────────────────────────────
//  Пустое состояние
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyFeed() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("📭", fontSize = 56.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text("Лента пуста", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Подпишись на организаторов или жанры, чтобы видеть актуальные события",
            color = NightTextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp,
        )
    }
}

private fun categoryEmoji(category: String) = when (category.lowercase()) {
    "концерт", "концерты" -> "🎵"
    "спорт"               -> "⚽"
    "йога"                -> "🧘"
    "квиз", "квизы"       -> "🧩"
    "арт", "выставка"     -> "🎨"
    "еда", "фуд"          -> "🍕"
    else                  -> "📅"
}
