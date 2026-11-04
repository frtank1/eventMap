package com.eventmap.feed.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.feed.models.SubscriptionItem
import com.eventmap.feed.models.SubscriptionRequest
import com.eventmap.feed.ui.viewmodel.SubTab
import com.eventmap.feed.ui.viewmodel.SubscriptionsViewModel
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.Danger
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.NightTextTertiary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  F6 · Подписки (Aurora Violet, ночная тема)
// =====================================================================

class SubscriptionsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<SubscriptionsViewModel>()
        val tab by vm.tab.collectAsState()
        val items by vm.items.collectAsState()
        val requests by vm.requests.collectAsState()

        Column(
            Modifier
                .fillMaxSize()
                .background(NightBackground),
        ) {
            com.eventmap.base_screen.RequestErrorBanner(vm, onRetry = vm::reload)
            // Топ-бар
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text("Подписки", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            // Таб-бар
            SubTabBar(tab, onSelect = { vm.setTab(it) }, Modifier.padding(horizontal = 16.dp))

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (requests.isNotEmpty()) {
                    item {
                        Text(
                            "ЗАПРОСЫ НА ПОДПИСКУ",
                            color = NightTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(requests, key = { it.id }) { r ->
                        RequestRow(r, onApprove = { vm.approve(r.id) }, onReject = { vm.reject(r.id) })
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                if (items.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 60.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("🔍", fontSize = 40.sp)
                            Text("Подписок нет", color = NightTextSecondary, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(items, key = { it.id }) { s -> SubRow(s) }
                }

                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Таб-бар (Организаторы / Жанры / Люди)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SubTabBar(selected: SubTab, onSelect: (SubTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SubTab.entries.forEach { t ->
            val active = t == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (active) Brush.linearGradient(BrandGradient)
                        else Brush.linearGradient(listOf(NightSurface, NightSurface)),
                    )
                    .clickableNoRipple { onSelect(t) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.label,
                    color = if (active) Color.White else NightTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Строка подписки
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SubRow(s: SubscriptionItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NightSurface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Violet600.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(s.emoji.ifBlank { "•" }, fontSize = 18.sp)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (s.subtitle.isNotBlank()) {
                Text(s.subtitle, fontSize = 12.sp, color = NightTextTertiary, maxLines = 1)
            }
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .clickableNoRipple { }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text("Отписаться", fontSize = 12.sp, color = NightTextSecondary)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Строка запроса
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RequestRow(r: SubscriptionRequest, onApprove: () -> Unit, onReject: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Violet600.copy(alpha = 0.10f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Violet400.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(r.name.take(1).uppercase(), fontSize = 16.sp, color = Violet400, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            if (r.subtitle.isNotBlank()) {
                Text(r.subtitle, fontSize = 12.sp, color = NightTextTertiary)
            }
        }
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = "Принять",
            tint = Soft,
            modifier = Modifier.clickableNoRipple(onApprove).padding(horizontal = 8.dp).size(26.dp),
        )
        Icon(
            Icons.Rounded.Cancel,
            contentDescription = "Отклонить",
            tint = Danger,
            modifier = Modifier.clickableNoRipple(onReject).padding(horizontal = 8.dp).size(26.dp),
        )
    }
}
