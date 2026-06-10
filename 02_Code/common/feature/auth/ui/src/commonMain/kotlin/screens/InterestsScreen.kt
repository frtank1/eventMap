package com.eventmap.auth.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.auth.ui.HomeScreenProvider
import com.eventmap.auth.ui.screens.components.LinkText
import com.eventmap.auth.ui.viewmodel.InterestsViewModel
import com.eventmap.theme.EventMapTheme
import com.eventmap.theme.ui_kit.PrimaryGradientButton
import com.eventmap.theme.ui_kit.clickableNoRipple
import org.koin.mp.KoinPlatform.getKoin

class InterestsScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<InterestsViewModel>()
        val snackbar = remember { SnackbarHostState() }
        val selected by vm.selected.collectAsState()
        val loading by vm.loading.collectAsState()
        val done by vm.done.collectAsState()
        val homeProvider = remember { getKoin().get<HomeScreenProvider>() }

        LaunchedEffect(Unit) { vm.errorMessage.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(done) { if (done) nav.replaceAll(homeProvider.home()) }

        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
            Column(modifier = Modifier.fillMaxSize().padding(inner).padding(24.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    LinkText("Пропустить", onClick = { vm.skip() })
                }
                Spacer(Modifier.height(8.dp))
                Text("Что тебе интересно?", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Text("Выбери жанры — подберём события под тебя", style = MaterialTheme.typography.bodyLarge, color = EventMapTheme.colors.textSecondary)
                Spacer(Modifier.height(20.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(vm.catalog) { interest ->
                        val isSel = interest.id in selected
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSel) EventMapTheme.colors.accent.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickableNoRipple { vm.toggle(interest.id) }
                                .padding(vertical = 16.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(interest.emoji, fontSize = 28.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                interest.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSel) EventMapTheme.colors.accent else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                PrimaryGradientButton(
                    text = if (selected.isEmpty()) "Готово" else "Готово · выбрано ${selected.size}",
                    loading = loading,
                    onClick = { vm.submit() },
                )
            }
        }
    }
}
