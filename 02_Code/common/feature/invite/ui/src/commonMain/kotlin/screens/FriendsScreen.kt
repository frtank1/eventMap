package com.eventmap.invite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.invite.ui.models.FriendItem
import com.eventmap.invite.ui.models.FriendRequest
import com.eventmap.invite.ui.models.PresenceColor
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
//  N1 · Мои друзья
// =====================================================================

class FriendsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

        val requests = remember { sampleRequests() }
        val friends  = remember { sampleFriends() }

        LazyColumn(
            Modifier.fillMaxSize().background(NightBackground),
        ) {
            // Шапка
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 56.dp, start = 20.dp, end = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Друзья", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("${friends.size} друзей", fontSize = 13.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(BrandGradient))
                            .clickableNoRipple { nav.push(CreateInviteScreen()) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) { Text("💌 Создать приглашение", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
            }

            // Поиск
            item {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp)).background(NightSurface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔍", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("Поиск по имени или @username", color = NightTextSecondary, fontSize = 14.sp)
                    }
                }
            }

            // Заявки в друзья
            if (requests.isNotEmpty()) {
                item {
                    SectionLabel("ЗАЯВКИ В ДРУЗЬЯ · ${requests.size}")
                }
                items(requests, key = { "req_${it.id}" }) { r ->
                    FriendRequestRow(r, onAccept = {}, onDecline = {})
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // Список друзей
            item { SectionLabel("МОИ ДРУЗЬЯ") }
            items(friends, key = { it.id }) { f ->
                FriendRow(f, onInvite = { nav.push(CreateInviteScreen()) })
            }

            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Строка заявки
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FriendRequestRow(r: FriendRequest, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Violet600.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)),
            contentAlignment = Alignment.Center,
        ) { Text(r.name.take(1).uppercase(), fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.ExtraBold) }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(r.name, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            if (r.commonLabel.isNotBlank()) {
                Text(r.commonLabel, fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Soft.copy(alpha = 0.20f))
                    .border(1.dp, Soft.copy(alpha = 0.40f), CircleShape).clickableNoRipple(onAccept),
                contentAlignment = Alignment.Center,
            ) { Text("✓", fontSize = 16.sp, color = Soft, fontWeight = FontWeight.Bold) }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Danger.copy(alpha = 0.12f))
                    .border(1.dp, Danger.copy(alpha = 0.30f), CircleShape).clickableNoRipple(onDecline),
                contentAlignment = Alignment.Center,
            ) { Text("✕", fontSize = 14.sp, color = Danger) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Строка друга
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FriendRow(f: FriendItem, onInvite: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Violet600.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) { Text(f.name.take(1).uppercase(), fontSize = 18.sp, color = Violet400, fontWeight = FontWeight.ExtraBold) }

            // Присутствие-точка
            if (f.presenceColor != PresenceColor.None) {
                Box(
                    Modifier.size(12.dp).clip(CircleShape).background(NightSurface).padding(2.dp),
                ) {
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape).background(
                            when (f.presenceColor) {
                                PresenceColor.Green  -> Soft
                                PresenceColor.Yellow -> Color(0xFFFFB300)
                                else                 -> Color.Transparent
                            },
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(f.name, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("@${f.username}", fontSize = 12.sp, color = NightTextTertiary)
            if (f.presenceLabel.isNotBlank()) {
                Text(f.presenceLabel, fontSize = 11.sp, color = when (f.presenceColor) {
                    PresenceColor.Green  -> Soft
                    PresenceColor.Yellow -> Color(0xFFFFB300)
                    else                 -> NightTextSecondary
                }, modifier = Modifier.padding(top = 2.dp))
            }
        }

        Box(
            Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                .clickableNoRipple(onInvite).padding(9.dp),
        ) { Text("💌", fontSize = 16.sp) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
//  Демо
// ─────────────────────────────────────────────────────────────────────────────

private fun sampleRequests() = listOf(
    FriendRequest("r1", "Айдана Сейткали", "2 общих события"),
    FriendRequest("r2", "Нурлан К.", "3 общих друга"),
)

private fun sampleFriends() = listOf(
    FriendItem("f1", "Тимур Ахметов", "timur_a", "🟢 сейчас на «Квиз-вечер»", PresenceColor.Green),
    FriendItem("f2", "Зарина", "zarina", "рядом · 1.2 км", PresenceColor.Yellow),
    FriendItem("f3", "Данияр", "daniyar_kz", "был онлайн 2 ч назад"),
    FriendItem("f4", "Арайлым", "arailym", ""),
)
