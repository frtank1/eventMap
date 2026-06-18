package com.eventmap.auth.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.eventmap.auth.ui.screens.components.LinkText
import com.eventmap.auth.ui.viewmodel.InterestsViewModel
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple
import org.koin.mp.KoinPlatform.getKoin
import com.eventmap.auth.ui.HomeScreenProvider

// =====================================================================
//  A8 — Выбор интересов (сетка чипов-жанров)
//  После выбора → B1 (разрешения) → карта
// =====================================================================

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
        LaunchedEffect(done) {
            if (done) nav.replaceAll(PermissionsScreen())
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NightBackground)
                .padding(horizontal = 20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Что тебе интересно?",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Выбери ≥3 жанра — подберём события под тебя",
                        fontSize = 13.sp,
                        color = NightTextSecondary,
                    )
                }
                LinkText("Пропустить", onClick = { vm.skip() })
            }

            // Счётчик выбранных
            if (selected.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Violet600.copy(alpha = 0.20f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        "Выбрано: ${selected.size} ✓",
                        color = Violet400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(vm.catalog) { interest ->
                    val isSel = interest.id in selected
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSel) Violet600.copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.06f),
                            )
                            .border(
                                1.dp,
                                if (isSel) Violet400.copy(alpha = 0.5f) else Color.Transparent,
                                RoundedCornerShape(14.dp),
                            )
                            .clickableNoRipple { vm.toggle(interest.id) }
                            .padding(vertical = 16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(interest.emoji, fontSize = 26.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            interest.title,
                            fontSize = 12.sp,
                            color = if (isSel) Violet400 else NightTextSecondary,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // CTA
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected.size >= 3) Brush.linearGradient(BrandGradient)
                        else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.12f))),
                    )
                    .clickableNoRipple { if (selected.size >= 3 && !loading) vm.submit() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        loading -> "Сохраняем…"
                        selected.isEmpty() -> "Выбери хотя бы 3"
                        selected.size < 3 -> "Ещё ${3 - selected.size}…"
                        else -> "Готово · на карту →"
                    },
                    color = if (selected.size >= 3) Color.White else NightTextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Spacer(Modifier.height(28.dp))
            SnackbarHost(snackbar)
        }
    }
}
