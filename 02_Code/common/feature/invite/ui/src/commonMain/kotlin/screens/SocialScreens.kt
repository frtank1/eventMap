package com.eventmap.invite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  Социальные экраны
//  FollowRequestsScreen    — входящие запросы на подписку
//  PeopleYouMayKnowScreen  — рекомендованные пользователи
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)
private val Coral500 = Color(0xFFFF5C8A)

private data class SocialUser(
    val id: String,
    val name: String,
    val username: String,
    val avatarEmoji: String,
    val mutualCount: Int,
    val level: Int,
    val events: Int,
    val bio: String = "",
)

private val pendingRequests = listOf(
    SocialUser("u1", "Баглан Оспанов", "@baglan_almaty", "🏆", 3, 17, 85, "Люблю джаз и уличное искусство"),
    SocialUser("u2", "Камила Рза", "@kamila_rza", "🎵", 1, 16, 79, "Музыкант · Алматы"),
    SocialUser("u3", "Серик Алиев", "@serik_art", "🎨", 5, 15, 71, "Художник, фотограф"),
    SocialUser("u4", "Жанна Смаил", "@zhanna_s", "🌿", 2, 14, 65, "Эко-активист"),
)

private val suggestedUsers = listOf(
    SocialUser("s1", "Айгерим Нурова", "@aigul_nur", "✨", 7, 21, 114, "Event-менеджер · 7 общих друзей"),
    SocialUser("s2", "Нурсұлтан Бек", "@nur_bek", "🎯", 4, 18, 92, "Барабанщик · Jazz Квартал"),
    SocialUser("s3", "Дина Сейткали", "@dina_k", "🌸", 2, 12, 58, "Фотограф событий"),
    SocialUser("s4", "Алибек Жаксы", "@alibek_j", "🎬", 6, 20, 108, "Режиссёр · Кинофестивали"),
    SocialUser("s5", "Мадина Рахим", "@madina_r", "🎤", 3, 15, 73, "Стендап-комик · Comedy Club KZ"),
    SocialUser("s6", "Ержан Абен", "@erzhan_a", "🎸", 1, 11, 47, "Гитарист · Рок-концерты"),
)

// ─────────────────────────────────────────────────────────────────────────────
//  FollowRequestsScreen — входящие запросы
// ─────────────────────────────────────────────────────────────────────────────

class FollowRequestsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val accepted = remember { mutableStateOf(setOf<String>()) }
        val declined = remember { mutableStateOf(setOf<String>()) }
        val pending = pendingRequests.filter { it.id !in accepted.value && it.id !in declined.value }

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Запросы на подписку", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("${pending.size} ожидает", fontSize = 12.sp, color = NightTextSecondary)
                }
                if (pending.isNotEmpty()) {
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                            .clickableNoRipple { accepted.value = accepted.value + pending.map { it.id } }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) { Text("Все ✓", fontSize = 12.sp, color = NightTextSecondary) }
                }
            }

            if (pending.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✓", fontSize = 48.sp, color = Soft)
                        Spacer(Modifier.height(12.dp))
                        Text("Все запросы обработаны", fontSize = 15.sp, color = NightTextSecondary)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(pending) { user ->
                        RequestRow(
                            user = user,
                            onAccept = { accepted.value = accepted.value + user.id },
                            onDecline = { declined.value = declined.value + user.id },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestRow(user: SocialUser, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(Brush.linearGradient(BrandGradient)),
                contentAlignment = Alignment.Center,
            ) { Text(user.avatarEmoji, fontSize = 22.sp) }
            Box(
                Modifier.size(18.dp).clip(CircleShape).background(NightBackground).border(1.dp, Violet600, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("${user.level}", fontSize = 8.sp, color = Violet400, fontWeight = FontWeight.ExtraBold) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(user.name, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(user.username, fontSize = 11.sp, color = NightTextSecondary)
            if (user.mutualCount > 0) {
                Spacer(Modifier.height(3.dp))
                Text("${user.mutualCount} общих друга", fontSize = 10.sp, color = Violet400)
            }
        }
        Spacer(Modifier.width(8.dp))
        // Принять
        Box(
            Modifier.height(34.dp).clip(RoundedCornerShape(999.dp)).background(Brush.linearGradient(BrandGradient))
                .clickableNoRipple(onAccept).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
        Spacer(Modifier.width(6.dp))
        // Отклонить
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.08f))
                .clickableNoRipple(onDecline),
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = NightTextSecondary, fontSize = 14.sp) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PeopleYouMayKnowScreen — рекомендации
// ─────────────────────────────────────────────────────────────────────────────

class PeopleYouMayKnowScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val following = remember { mutableStateOf(setOf<String>()) }
        var selectedFilter by remember { mutableStateOf(0) }
        val filters = listOf("Все", "По интересам", "Рядом", "По событиям")

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 12.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f)).clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text("Люди рядом с вами", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            // Фильтры
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filters.forEachIndexed { i, f ->
                    val sel = i == selectedFilter
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
                            .border(1.dp, if (!sel) Color.White.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(999.dp))
                            .clickableNoRipple { selectedFilter = i }.padding(horizontal = 12.dp, vertical = 7.dp),
                    ) { Text(f, fontSize = 12.sp, color = if (sel) Color.White else NightTextSecondary) }
                }
            }

            Spacer(Modifier.height(4.dp))

            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(suggestedUsers) { user ->
                    SuggestedUserRow(
                        user = user,
                        isFollowing = user.id in following.value,
                        onFollowToggle = {
                            following.value = if (user.id in following.value)
                                following.value - user.id else following.value + user.id
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedUserRow(user: SocialUser, isFollowing: Boolean, onFollowToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.size(50.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Violet600.copy(alpha = 0.30f), Violet600.copy(alpha = 0.30f)))),
                contentAlignment = Alignment.Center,
            ) { Text(user.avatarEmoji, fontSize = 22.sp) }
            Box(
                Modifier.size(18.dp).clip(CircleShape).background(NightBackground).border(1.dp, Violet600, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("${user.level}", fontSize = 8.sp, color = Violet400, fontWeight = FontWeight.ExtraBold) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(user.name, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(user.username, fontSize = 11.sp, color = NightTextSecondary)
            Spacer(Modifier.height(3.dp))
            Text(user.bio, fontSize = 11.sp, color = NightTextTertiary, maxLines = 1)
            if (user.mutualCount > 0) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👥", fontSize = 10.sp)
                    Spacer(Modifier.width(3.dp))
                    Text("${user.mutualCount} общих", fontSize = 10.sp, color = Violet400)
                    Text(" · ", fontSize = 10.sp, color = NightTextTertiary)
                    Text("${user.events} событий", fontSize = 10.sp, color = NightTextTertiary)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.height(34.dp).clip(RoundedCornerShape(999.dp))
                .background(
                    if (isFollowing) Brush.linearGradient(listOf(NightSurface, NightSurface))
                    else Brush.linearGradient(BrandGradient)
                )
                .border(1.dp, if (isFollowing) Color.White.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(999.dp))
                .clickableNoRipple(onFollowToggle).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isFollowing) "Подписан" else "Подписаться",
                color = if (isFollowing) NightTextSecondary else Color.White,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
