package com.eventmap

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
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
import com.eventmap.map.city.GameCityContent
import com.eventmap.profile.ui.ProfileContent
import com.eventmap.profile.ui.screens.EditProfileScreen
import com.eventmap.profile.ui.screens.SettingsScreen
import com.eventmap.search.ui.screens.SearchScreen
import com.eventmap.video.ui.screens.VideoFeedScreen
import com.eventmap.video.ui.screens.VideoFeedContent
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
import com.eventmap.theme.Violet400
import com.eventmap.theme.NightOutline
import com.eventmap.theme.NightAccentSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  Главный экран — Mobile Polish
//  Таббар: Карта · Сейчас · [+ событие] · Квесты · Профиль
//  Лента/уведомления F4 — по колокольчику на вкладке «Сейчас»
// =====================================================================

private val NightTextTertiary = NightTextSecondary

private data class NavTab(val icon: ImageVector?, val label: String)

private val navTabs = listOf(
    NavTab(EventMapIcons.Map, "Карта"),
    NavTab(EventMapIcons.Video, "Сейчас"),
    NavTab(null, ""),                         // центральная кнопка — создать событие
    NavTab(EventMapIcons.Reels, "Квесты"),
    NavTab(EventMapIcons.Profile, "Профиль"),
)

class MainScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selected by rememberSaveable { mutableStateOf(0) }
        var gameCity by rememberSaveable { mutableStateOf(false) }

        // Дошли до главного экрана после входа — один раз предложим настроить PIN.
        val appLock = remember { getKoin().get<AppLockController>() }
        LaunchedEffect(Unit) { appLock.onHomeEntered() }

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (selected) {
                    0 -> if (gameCity) GameCityContent(
                        modifier=Modifier.fillMaxSize(),
                        onExit={gameCity=false},
                        onOpenEvent={id -> nav.push(EventReelScreen(listOf(EventDetail(id=id,title=""))))},
                        onCreateHere={point -> nav.push(CreateEventScreen(initialLat=point.lat,initialLng=point.lng,initialLocationName=point.locationLabel,initialVenue=point.venue))},
                    ) else Box(Modifier.fillMaxSize()) {
                        MapContent(
                            modifier = Modifier.fillMaxSize(),
                            onOpenEvent = { id -> nav.push(EventReelScreen(listOf(EventDetail(id = id, title = "")))) },
                            onSearchClick = { nav.push(SearchScreen()) },
                            onOpenLive = { nav.push(VideoFeedScreen()) },
                            // «Я тоже здесь · снять момент»: рилс с привязкой к событию
                            onRecordMoment = { id, title -> nav.push(RecordVideoScreen(eventId = id, eventTitle = title)) },
                        )
                        androidx.compose.material3.FilledTonalButton(
                            onClick={gameCity=true},
                            modifier=Modifier.align(Alignment.BottomEnd).padding(end=12.dp,bottom=100.dp),
                        ) { Text("3D-город · пилот") }
                    }
                    // «Сейчас»: ролики участников + колокольчик (лента/уведомления)
                    1 -> VideoFeedContent(onNotifications = { nav.push(FeedScreen()) })
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
                // съёмка момента — из события, с сохранением его идентификатора.
                onCreateClick = { nav.push(CreateEventScreen()) },
            )
        }
    }
}

@Composable
fun BottomNavBar(selected: Int, onSelect: (Int) -> Unit, onCreateClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(NightSurface),
    ) {
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .background(NightOutline)
                .align(Alignment.TopCenter),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp, start = 9.dp, end = 9.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navTabs.forEachIndexed { i, tab ->
                if (i == 2) {
                    Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) { CreateFab(onCreateClick) }
                } else {
                    NavItem(tab = tab, active = selected == i, onClick = { onSelect(i) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NavItem(tab: NavTab, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val labelColor by animateColorAsState(
        targetValue = if (active) Violet400 else NightTextSecondary,
        animationSpec = tween(200),
    )
    Column(
        modifier.height(54.dp).clip(RoundedCornerShape(15.dp))
            .background(if (active) NightAccentSurface else Color.Transparent)
            .selectable(selected = active, role = Role.Tab, onClick = onClick).padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = tab.icon!!,
            contentDescription = null,
            tint = labelColor,
            modifier = Modifier.size(22.dp),
        )
        Text(
            tab.label, color = labelColor, fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CreateFab(onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(BrandGradient))
            .clickable(role = Role.Button, onClick = onClick),
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
