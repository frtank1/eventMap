package com.eventmap

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.applock.AppLockController
import com.eventmap.auth.ui.screens.SplashScreen
import org.koin.mp.KoinPlatform.getKoin
import com.eventmap.event.ui.screens.CreateEventScreen
import com.eventmap.event.ui.screens.MyEventsScreen
import com.eventmap.event.ui.screens.EventReelScreen
import com.eventmap.event.models.EventDetail
import com.eventmap.feed.ui.FeedContent
import com.eventmap.feed.ui.NotificationsContent
import com.eventmap.feed.ui.screens.SubscriptionsScreen
import com.eventmap.map.screen.MapContent
import com.eventmap.profile.ui.ProfileContent
import com.eventmap.profile.ui.screens.EditProfileScreen
import com.eventmap.profile.ui.screens.SettingsScreen
import com.eventmap.search.ui.screens.SearchScreen
import com.eventmap.video.ui.screens.VideoFeedScreen
import com.eventmap.video.ui.screens.RecordVideoScreen
import com.eventmap.quest.ui.screens.QuestCatalogScreen
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.eventmap.theme.BrandGradient
import com.eventmap.business.ui.screens.BusinessGateScreen
import com.eventmap.theme.EventMapIcons
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  Главный экран — Aurora Violet v2
//  Таббар по дизайн-прототипу (BOTTOM NAV): Карта · ⚡Сейчас · [🎬 запись] · 🎮Квесты · Профиль
//  Лента/уведомления F4 — по колокольчику на вкладке «Сейчас»
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)

private data class NavTab(val icon: ImageVector?, val label: String)

private val navTabs = listOf(
    NavTab(EventMapIcons.Map, "Карта"),
    NavTab(EventMapIcons.Video, "Сейчас"),   // ⚡ live-лента (E1/E2)
    NavTab(null, ""),                         // центральный FAB — 🎬 снять момент (E3)
    NavTab(EventMapIcons.Reels, "Квесты"),   // 🎮 Q1 каталог
    NavTab(EventMapIcons.Profile, "Профиль"),
)

class MainScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selected by remember { mutableStateOf(0) }

        // Дошли до главного экрана после входа — один раз предложим настроить PIN.
        val appLock = remember { getKoin().get<AppLockController>() }
        LaunchedEffect(Unit) { appLock.onHomeEntered() }

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (selected) {
                    0 -> MapContent(
                        modifier = Modifier.fillMaxSize(),
                        onOpenEvent = { id -> nav.push(EventReelScreen(listOf(EventDetail(id = id, title = "")))) },
                        onSearchClick = { nav.push(SearchScreen()) },
                        onOpenLive = { nav.push(VideoFeedScreen()) },
                        // «Я тоже здесь · снять момент»: рилс с привязкой к событию
                        onRecordMoment = { id, title -> nav.push(RecordVideoScreen(eventId = id, eventTitle = title)) },
                    )
                    // ⚡ «Сейчас»: live-видео + колокольчик (лента/уведомления F4)
                    1 -> Box(Modifier.fillMaxSize()) {
                        VideoFeedScreen().Content()
                        Box(
                            Modifier.align(Alignment.TopEnd)
                                .padding(top = 52.dp, end = 16.dp)
                                .size(40.dp).clip(CircleShape)
                                .background(NightSurface.copy(alpha = 0.85f))
                                .clickableNoRipple { nav.push(FeedScreen()) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                EventMapIcons.Bell,
                                contentDescription = "Уведомления",
                                tint = Color.White,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                    // 🎮 Квесты (Q1, по дизайну — своя вкладка)
                    3 -> QuestCatalogScreen().Content()
                    4 -> ProfileContent(
                        modifier = Modifier.fillMaxSize(),
                        onLogout = {
                            appLock.onLogout()
                            nav.replaceAll(SplashScreen())
                        },
                        onOpenEvent = { id -> nav.push(EventReelScreen(listOf(EventDetail(id = id, title = "")))) },
                        onEditProfile = { name, bio -> nav.push(EditProfileScreen(name, bio)) },
                        onOpenSettings = { nav.push(SettingsScreen()) },
                        onOpenOrganizer = { nav.push(BusinessGateScreen()) }, // гейт: промо / статус заявки / дашборд
                        onOpenMyEvents = { nav.push(MyEventsScreen()) },
                    )
                    else -> NotificationsContent(Modifier.fillMaxSize())
                }
            }

            BottomNavBar(
                selected = selected,
                onSelect = { selected = it },
                // Центр таббара — ＋ создать событие (привычный UX);
                // съёмка момента 🎬 — из ленты «Сейчас» (кнопка в топбаре VideoFeedScreen).
                onCreateClick = { nav.push(CreateEventScreen()) },
            )
        }
    }
}

@Composable
private fun BottomNavBar(selected: Int, onSelect: (Int) -> Unit, onCreateClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(NightSurface),
    ) {
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .background(Color.White.copy(alpha = 0.06f))
                .align(Alignment.TopCenter),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navTabs.forEachIndexed { i, tab ->
                if (i == 2) {
                    CreateFab(onCreateClick)
                } else {
                    NavItem(tab = tab, active = selected == i, onClick = { onSelect(i) })
                }
            }
        }
    }
}

@Composable
private fun NavItem(tab: NavTab, active: Boolean, onClick: () -> Unit) {
    val labelColor by animateColorAsState(
        targetValue = if (active) Violet600 else NightTextTertiary,
        animationSpec = tween(200),
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.5f),
    )

    Column(
        Modifier.width(64.dp).clickableNoRipple(onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = tab.icon!!,
            contentDescription = tab.label,
            tint = labelColor,
            modifier = Modifier.size(24.dp).scale(scale),
        )
        Text(
            tab.label, color = labelColor, fontSize = 10.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier.size(4.dp).clip(CircleShape)
                .background(if (active) Violet600 else Color.Transparent),
        )
    }
}

@Composable
private fun CreateFab(onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape)
            .background(Brush.linearGradient(BrandGradient))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            EventMapIcons.Plus,
            contentDescription = "Создать событие",
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

// Обёртка «Лента / Уведомления» (F1/F4) — вход по колокольчику с вкладки «Сейчас»
private class FeedScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var tab by remember { mutableStateOf(0) }

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 52.dp, start = 20.dp, end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Лента", "Уведомления").forEachIndexed { i, label ->
                    val active = tab == i
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (active) Violet600.copy(alpha = 0.28f) else NightSurface)
                            .clickableNoRipple { tab = i }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            label,
                            color = if (active) Color.White else NightTextTertiary,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                if (tab == 0) {
                    FeedContent(
                        modifier = Modifier.fillMaxSize(),
                        onOpenEvent = { id -> nav.push(EventReelScreen(listOf(EventDetail(id = id, title = "")))) },
                        onOpenSubscriptions = { nav.push(SubscriptionsScreen()) },
                    )
                } else {
                    NotificationsContent(Modifier.fillMaxSize())
                }
            }
        }
    }
}
