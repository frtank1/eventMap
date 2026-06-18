package com.eventmap.profile.ui.screens

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
import androidx.compose.ui.graphics.SolidColor
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
//  FollowersFollowingScreen — список подписчиков / подписок
//  Единый экран с переключением через selectedTab
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)

private data class UserListItem(
    val id: String,
    val name: String,
    val username: String,
    val avatarEmoji: String,
    val level: Int,
    val mutualCount: Int,
    val isFollowingBack: Boolean = false,
)

private val sampleFollowers = listOf(
    UserListItem("u1", "Тимур Ахметов", "@timur_jazz", "😎", 24, 0, true),
    UserListItem("u2", "Зарина Кадырова", "@zarina_k", "🥰", 18, 3, true),
    UserListItem("u3", "Айгерим Нурова", "@aigul_nur", "✨", 21, 7, false),
    UserListItem("u4", "Данияр Асанов", "@daniyar_a", "🤓", 20, 4, true),
    UserListItem("u5", "Баглан Оспанов", "@baglan_o", "🏆", 17, 1, false),
    UserListItem("u6", "Камила Рза", "@kamila_rza", "🎵", 16, 2, false),
    UserListItem("u7", "Серик Алиев", "@serik_art", "🎨", 15, 5, true),
    UserListItem("u8", "Жанна Смаил", "@zhanna_s", "🌿", 14, 0, false),
    UserListItem("u9", "Арайлым Сейт", "@araylum_s", "😄", 19, 2, true),
    UserListItem("u10", "Нурсұлтан Бек", "@nur_bek", "🎯", 18, 3, false),
)

private val sampleFollowing = sampleFollowers.filter { it.isFollowingBack }.shuffled().map { it.copy(isFollowingBack = true) }

data class FollowersFollowingScreen(
    val userId: String,
    val userName: String,
    val initialTab: Int = 0, // 0 = подписчики, 1 = подписки
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selectedTab by remember { mutableStateOf(initialTab) }
        var searchQuery by remember { mutableStateOf("") }
        val followState = remember { mutableStateOf(sampleFollowing.map { it.id }.toSet()) }

        val list = (if (selectedTab == 0) sampleFollowers else sampleFollowing)
            .filter { searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true) || it.username.contains(searchQuery, ignoreCase = true) }

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
                Text(userName, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            // Табы
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                listOf("Подписчики ${sampleFollowers.size}", "Подписки ${sampleFollowing.size}").forEachIndexed { i, label ->
                    val sel = i == selectedTab
                    Box(
                        Modifier.weight(1f).clickableNoRipple { selectedTab = i }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, fontSize = 13.sp, color = if (sel) Color.White else NightTextSecondary, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Normal)
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth(0.5f).height(2.dp).clip(RoundedCornerShape(999.dp)).background(if (sel) Brush.linearGradient(BrandGradient) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))))
                        }
                    }
                }
            }

            // Поиск
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(42.dp)
                    .clip(RoundedCornerShape(12.dp)).background(NightSurface),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔍", fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Поиск...", fontSize = 13.sp, color = NightTextSecondary)
                }
            }

            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(list) { user ->
                    val isFollowing = user.id in followState.value
                    UserListRow(
                        user = user,
                        isFollowing = isFollowing,
                        showFollowButton = selectedTab == 0 && !user.isFollowingBack,
                        onFollowToggle = {
                            followState.value = if (isFollowing) followState.value - user.id else followState.value + user.id
                        },
                        onUserClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun UserListRow(
    user: UserListItem,
    isFollowing: Boolean,
    showFollowButton: Boolean,
    onFollowToggle: () -> Unit,
    onUserClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickableNoRipple(onUserClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Аватар + уровень
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
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
            Spacer(Modifier.height(1.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.username, fontSize = 11.sp, color = NightTextSecondary)
                if (user.mutualCount > 0) {
                    Text("  ·  ", fontSize = 11.sp, color = NightTextTertiary)
                    Text("${user.mutualCount} общих", fontSize = 11.sp, color = NightTextTertiary)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (showFollowButton) {
            // Кнопка «Подписаться в ответ»
            Box(
                Modifier.height(32.dp).clip(RoundedCornerShape(999.dp))
                    .background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple(onFollowToggle).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Подписаться", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        } else if (user.isFollowingBack || !showFollowButton) {
            Box(
                Modifier.height(32.dp).clip(RoundedCornerShape(999.dp))
                    .background(if (isFollowing) SolidColor(NightSurface) else Brush.linearGradient(BrandGradient))
                    .border(1.dp, if (isFollowing) Color.White.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(999.dp))
                    .clickableNoRipple(onFollowToggle).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isFollowing) "Подписан" else "Подписаться",
                    color = if (isFollowing) NightTextSecondary else Color.White,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).padding(start = 76.dp, end = 16.dp).background(Color.White.copy(alpha = 0.04f)))
}
