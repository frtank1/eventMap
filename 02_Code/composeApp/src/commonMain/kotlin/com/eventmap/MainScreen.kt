package com.eventmap

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.auth.ui.screens.SplashScreen
import com.eventmap.event.ui.screens.CreateEventScreen
import com.eventmap.event.ui.screens.EventDetailScreen
import com.eventmap.feed.ui.FeedContent
import com.eventmap.feed.ui.NotificationsContent
import com.eventmap.feed.ui.screens.SubscriptionsScreen
import com.eventmap.map.screen.MapContent
import com.eventmap.profile.ui.ProfileContent
import com.eventmap.profile.ui.screens.EditProfileScreen
import com.eventmap.profile.ui.screens.SettingsScreen
import com.eventmap.search.ui.screens.SearchScreen
import com.eventmap.video.ui.screens.VideoFeedScreen
import com.eventmap.theme.EventMapTheme

private data class Tab(val icon: String, val label: String)

private val tabs = listOf(
    Tab("🗺️", "Карта"),
    Tab("📋", "Лента"),
    Tab("＋", "Создать"),
    Tab("🔔", "Уведомл."),
    Tab("👤", "Профиль"),
)

/** Главный экран приложения с нижней навигацией (app shell). */
class MainScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selected by remember { mutableStateOf(0) }

        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (selected) {
                    0 -> MapContent(
                        modifier = Modifier.fillMaxSize(),
                        onOpenEvent = { id -> nav.push(EventDetailScreen(id)) },
                        onSearchClick = { nav.push(SearchScreen()) },
                        onOpenLive = { nav.push(VideoFeedScreen()) },
                    )
                    1 -> FeedContent(
                        modifier = Modifier.fillMaxSize(),
                        onOpenEvent = { id -> nav.push(EventDetailScreen(id)) },
                        onOpenSubscriptions = { nav.push(SubscriptionsScreen()) },
                    )
                    3 -> NotificationsContent(Modifier.fillMaxSize())
                    else -> ProfileContent(
                        modifier = Modifier.fillMaxSize(),
                        onLogout = { nav.replaceAll(SplashScreen()) },
                        onOpenEvent = { id -> nav.push(EventDetailScreen(id)) },
                        onEditProfile = { name, bio -> nav.push(EditProfileScreen(name, bio)) },
                        onOpenSettings = { nav.push(SettingsScreen()) },
                    )
                }
            }
            BottomNav(
                selected = selected,
                onSelect = { selected = it },
                onCreate = { nav.push(CreateEventScreen()) },
            )
        }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit, onCreate: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, tab ->
            if (i == 2) {
                CreateFab(onClick = onCreate)
            } else {
                NavItem(tab = tab, active = selected == i, onClick = { onSelect(i) })
            }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, active: Boolean, onClick: () -> Unit) {
    val color = if (active) EventMapTheme.colors.accent else EventMapTheme.colors.textTertiary
    Column(
        modifier = Modifier.clickableTab(onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tab.icon, fontSize = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            tab.label,
            color = color,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CreateFab(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(EventMapTheme.colors.brandGradient))
            .clickableTab(onClick),
        contentAlignment = Alignment.Center,
    ) { Text("＋", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
}

// локальный clickable для элементов нижней навигации
private fun Modifier.clickableTab(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
