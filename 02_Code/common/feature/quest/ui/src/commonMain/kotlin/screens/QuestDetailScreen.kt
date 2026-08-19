package com.eventmap.quest.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.quest.ui.viewmodel.QuestDetailViewModel
import com.eventmap.quest.ui.models.PointStatus
import com.eventmap.quest.ui.models.QuestItem
import com.eventmap.quest.ui.models.QuestPoint
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.NightTextTertiary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  Q2 · Карточка квеста
// =====================================================================

data class QuestDetailScreen(val quest: QuestItem) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        // мгновенный рендер переданного квеста + фоновое обновление с бэка (GET /quests/{id})
        val vm = koinScreenModel<QuestDetailViewModel>()
        androidx.compose.runtime.LaunchedEffect(quest.id) { vm.load(quest) }
        val q = vm.quest.collectAsState().value ?: quest
        val progress = q.pointsDone.toFloat() / q.pointsTotal.coerceAtLeast(1)

        Column(
            Modifier
                .fillMaxSize()
                .background(NightBackground)
                .verticalScroll(rememberScrollState()),
        ) {
            // Топ-бар
            Row(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(q.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2)
                    Text(q.authorName, fontSize = 12.sp, color = Violet400, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
                }
            }

            // Мини-карта заглушка
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0D1A2A), Color(0xFF112030)))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🗺", fontSize = 40.sp)
                    Text("Маршрут квеста", color = NightTextSecondary, fontSize = 13.sp)
                }
                // Маркеры точек
                Row(
                    Modifier.align(Alignment.BottomStart).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    q.points.forEachIndexed { i, p ->
                        val (bg, text) = when (p.status) {
                            PointStatus.Done   -> Soft to "✓"
                            PointStatus.Active -> Violet600 to "${i + 1}"
                            PointStatus.Locked -> Color.White.copy(alpha = 0.20f) to "🔒"
                        }
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(bg),
                            contentAlignment = Alignment.Center,
                        ) { Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Прогресс
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Прогресс", fontSize = 13.sp, color = NightTextSecondary, fontWeight = FontWeight.Medium)
                    Text("${q.pointsDone} / ${q.pointsTotal} точек", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                    color = Violet600,
                    trackColor = Color.White.copy(alpha = 0.10f),
                    strokeCap = StrokeCap.Round,
                )
            }

            Spacer(Modifier.height(20.dp))

            // Список точек
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ТОЧКИ", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                q.points.forEachIndexed { i, p ->
                    QuestPointRow(index = i + 1, point = p, onGo = { nav.push(QuestCheckInScreen(p, quest = q)) })
                }
            }

            Spacer(Modifier.height(20.dp))

            // Награда
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Soft.copy(alpha = 0.10f))
                    .border(1.dp, Soft.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🎁", fontSize = 28.sp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Награда", fontSize = 11.sp, color = NightTextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Text(q.rewardLabel, fontSize = 15.sp, color = Soft, fontWeight = FontWeight.ExtraBold)
                    Text("${q.finishersCount} человек уже получили", fontSize = 11.sp, color = NightTextTertiary, modifier = Modifier.padding(top = 2.dp))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Строка точки квеста
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuestPointRow(index: Int, point: QuestPoint, onGo: () -> Unit) {
    val isActive = point.status == PointStatus.Active
    val isDone   = point.status == PointStatus.Done
    val isLocked = point.status == PointStatus.Locked

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    isDone   -> Soft.copy(alpha = 0.08f)
                    isActive -> Violet600.copy(alpha = 0.12f)
                    else     -> NightSurface
                },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Номер / статус
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(
                when {
                    isDone   -> SolidColor(Soft.copy(alpha = 0.20f))
                    isActive -> Brush.linearGradient(BrandGradient)
                    else     -> SolidColor(Color.White.copy(alpha = 0.08f))
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isDone) "✓" else if (isLocked) "🔒" else "$index",
                color = if (isDone) Soft else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(point.title, fontSize = 14.sp, color = if (isLocked) NightTextSecondary else Color.White, fontWeight = FontWeight.SemiBold)
            Text(point.address, fontSize = 12.sp, color = NightTextTertiary, modifier = Modifier.padding(top = 2.dp))
            Text(point.checkInType.emoji + " " + point.checkInType.label, fontSize = 11.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
        }

        if (isActive) {
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple(onGo)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("➜ Идти", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (point.distanceM != null) {
                    Text(formatDist(point.distanceM), color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                }
            }
        }
    }
}

private fun formatDist(m: Int) = if (m >= 1000) "${m / 1000}.${(m % 1000) / 100} км" else "$m м"
