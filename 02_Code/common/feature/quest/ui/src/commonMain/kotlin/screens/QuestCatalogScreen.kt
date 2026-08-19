package com.eventmap.quest.ui.screens

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.koin.koinScreenModel
import com.eventmap.quest.ui.viewmodel.QuestCatalogViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.quest.ui.models.CheckInType
import com.eventmap.quest.ui.models.PointStatus
import com.eventmap.quest.ui.models.QuestItem
import com.eventmap.quest.ui.models.QuestPoint
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.Info
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.NightTextTertiary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  Q1 · Каталог «Игры рядом»
// =====================================================================

class QuestCatalogScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

        // Данные с бэка (/quests/nearby); при недоступном бэке — демо (паттерн MapViewModel)
        val vm = koinScreenModel<QuestCatalogViewModel>()
        val quests by vm.items.collectAsState()

        Column(
            Modifier
                .fillMaxSize()
                .background(NightBackground),
        ) {
            // Шапка
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 56.dp, start = 20.dp, end = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Игры рядом", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("Квесты от бизнеса · получи приз", fontSize = 13.sp, color = NightTextSecondary, modifier = Modifier.padding(top = 2.dp))
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(BrandGradient))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("🗺 Карта", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(quests, key = { it.id }) { q ->
                    QuestCard(q, onClick = { nav.push(QuestDetailScreen(q)) })
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Карточка квеста (Q1)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun QuestCard(q: QuestItem, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NightSurface)
            .clickableNoRipple(onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Эмодзи-иконка
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(BrandGradient)),
                contentAlignment = Alignment.Center,
            ) {
                Text(q.emoji, fontSize = 24.sp)
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(q.title, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q.authorName, fontSize = 12.sp, color = Violet400, fontWeight = FontWeight.Medium)
                    if (q.isVerifiedBusiness) {
                        Spacer(Modifier.width(3.dp))
                        Text("✓", fontSize = 10.sp, color = Info, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Прогресс-бар (если в процессе)
        if (q.pointsDone > 0) {
            val progress = q.pointsDone.toFloat() / q.pointsTotal
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(999.dp)),
                color = Violet600,
                trackColor = Color.White.copy(alpha = 0.10f),
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(6.dp))
        }

        // Метаданные
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetaPill("${q.pointsDone}/${q.pointsTotal} точек")
            MetaPill(q.checkInType.emoji + " " + q.checkInType.label)
            MetaPill("⏱ ${q.deadlineLabel}")
        }

        Spacer(Modifier.height(10.dp))

        // Награда + финишёры
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Soft.copy(alpha = 0.15f))
                    .border(1.dp, Soft.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🎁", fontSize = 13.sp)
                Spacer(Modifier.width(5.dp))
                Text(q.rewardLabel, fontSize = 12.sp, color = Soft, fontWeight = FontWeight.SemiBold)
            }

            Text("${q.finishersCount} финишёров", fontSize = 11.sp, color = NightTextTertiary)
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 11.sp, color = NightTextSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Демо-данные (fallback при недоступном бэке; используются из questModule)
// ─────────────────────────────────────────────────────────────────────────────

internal fun sampleQuests() = listOf(
    QuestItem(
        id = "q1", emoji = "☕", title = "Кофейный тур по Байтереку",
        authorName = "Coffeemania", isVerifiedBusiness = true,
        pointsTotal = 4, pointsDone = 2, checkInType = CheckInType.Qr,
        deadlineLabel = "3 дня", finishersCount = 87,
        rewardLabel = "Бесплатный кофе",
        points = listOf(
            QuestPoint("p1", "Coffeemania Expo", "ул. Сауран 2", CheckInType.Qr, PointStatus.Done),
            QuestPoint("p2", "Coffeemania Khan Shatyr", "ул. Туран 37", CheckInType.Qr, PointStatus.Done),
            QuestPoint("p3", "Coffeemania Park", "пр. Достык 5", CheckInType.Qr, PointStatus.Active, distanceM = 430),
            QuestPoint("p4", "Coffeemania Central", "ул. Кошкарбаева 2", CheckInType.Qr, PointStatus.Locked),
        ),
    ),
    QuestItem(
        id = "q2", emoji = "🏛️", title = "Архитектурный марафон",
        authorName = "Visit Astana", isVerifiedBusiness = true,
        pointsTotal = 5, pointsDone = 0, checkInType = CheckInType.Photo,
        deadlineLabel = "7 дней", finishersCount = 234,
        rewardLabel = "Билет в Экспо-музей",
    ),
    QuestItem(
        id = "q3", emoji = "🎨", title = "Граффити-охота",
        authorName = "Urban Art KZ", isVerifiedBusiness = false,
        pointsTotal = 3, pointsDone = 0, checkInType = CheckInType.Geo,
        deadlineLabel = "2 дня", finishersCount = 41,
        rewardLabel = "NFT-значок",
    ),
)
