package com.eventmap.profile.ui.screens

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
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Soft
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  PremiumPromoScreen  — EventMap Pro: фичи + выбор плана
//  PremiumSuccessScreen — подтверждение покупки подписки
// =====================================================================

private val NightTextTertiary = Color(0xFF56657A)
private val Gold = Color(0xFFFFD700)
private val Coral500 = Color(0xFFFF5C8A)
private val ProGradient = listOf(Gold.copy(alpha = 0.80f), Coral500)

private data class PremiumFeature(val emoji: String, val title: String, val desc: String)

private val features = listOf(
    PremiumFeature("🗺️", "Расширенная карта", "Тепловая карта событий, история мест, офлайн-карта Алматы"),
    PremiumFeature("🔔", "Умные уведомления", "Персонализированные алерты по интересам без спама"),
    PremiumFeature("📊", "Детальная аналитика", "Полная статистика посещаемости и активности"),
    PremiumFeature("🎟", "Ранний доступ", "Покупка билетов на 24 ч раньше до старта продаж"),
    PremiumFeature("✨", "Pro-бейдж", "Золотой бейдж на профиле, приоритет в поиске"),
    PremiumFeature("💬", "Приоритетный чат", "Прямая связь с организаторами + VIP-чаты событий"),
    PremiumFeature("🚫", "Без рекламы", "Полностью чистый интерфейс без рекламных баннеров"),
    PremiumFeature("🎁", "Бонусные квесты", "Эксклюзивные квесты с двойным XP каждую неделю"),
)

private data class PricePlan(val id: String, val label: String, val price: String, val perMonth: String, val badge: String?)

private val plans = listOf(
    PricePlan("monthly", "1 месяц", "1 490 ₸", "1 490 ₸/мес", null),
    PricePlan("yearly", "12 месяцев", "9 990 ₸", "833 ₸/мес", "Экономия 44%"),
    PricePlan("halfyear", "6 месяцев", "5 990 ₸", "998 ₸/мес", "Популярное"),
)

// ─────────────────────────────────────────────────────────────────────────────
//  PremiumPromoScreen
// ─────────────────────────────────────────────────────────────────────────────

class PremiumPromoScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        var selectedPlan by remember { mutableStateOf("yearly") }

        Column(Modifier.fillMaxSize().background(NightBackground)) {
            // Хедер с градиентом
            Box(
                Modifier.fillMaxWidth().height(200.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF1A0A2E), Color(0xFF2D1B69), NightBackground))),
                contentAlignment = Alignment.Center,
            ) {
                // Закрыть
                Box(Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 16.dp)) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
                            .clickableNoRipple { nav.pop() },
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", color = Color.White, fontSize = 13.sp) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape)
                            .background(Brush.linearGradient(ProGradient))
                            .border(2.dp, Gold.copy(alpha = 0.50f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("✨", fontSize = 28.sp) }
                    Spacer(Modifier.height(10.dp))
                    Text("EventMap Pro", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text("Разблокируйте весь потенциал приложения", fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f))
                }
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // Фичи — 2 колонки
                val featureRows = features.chunked(2)
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    featureRows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { f ->
                                FeatureCard(Modifier.weight(1f), f)
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Планы
                Row(Modifier.padding(start = 20.dp, bottom = 10.dp)) {
                    Text("ВЫБЕРИТЕ ПЛАН", color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    plans.forEach { plan ->
                        PlanCard(plan, plan.id == selectedPlan) { selectedPlan = plan.id }
                    }
                }

                // Пробный период
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Soft.copy(alpha = 0.10f))
                        .border(1.dp, Soft.copy(alpha = 0.30f), RoundedCornerShape(12.dp)).padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎁", fontSize = 16.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("7 дней бесплатно", fontSize = 13.sp, color = Soft, fontWeight = FontWeight.SemiBold)
                            Text("Оплата начнётся только после пробного периода", fontSize = 11.sp, color = NightTextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            // Подписаться кнопка
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(ProGradient))
                        .clickableNoRipple { nav.replace(PremiumSuccessScreen()) },
                    contentAlignment = Alignment.Center,
                ) {
                    val plan = plans.find { it.id == selectedPlan }!!
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✨", fontSize = 16.sp)
                        Text("Попробовать Pro · ${plan.price}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Отмена в любой момент · Восстановить покупку",
                    fontSize = 11.sp, color = NightTextTertiary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(modifier: Modifier, feature: PremiumFeature) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(NightSurface).padding(12.dp),
    ) {
        Text(feature.emoji, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(feature.title, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(feature.desc, fontSize = 10.sp, color = NightTextSecondary, lineHeight = 14.sp)
    }
}

@Composable
private fun PlanCard(plan: PricePlan, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) Brush.linearGradient(listOf(Violet600.copy(alpha = 0.20f), Coral500.copy(alpha = 0.10f))) else Brush.linearGradient(listOf(NightSurface, NightSurface)))
            .border(2.dp, if (selected) Violet600.copy(alpha = 0.60f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickableNoRipple(onClick).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Радио-кнопка
            Box(
                Modifier.size(20.dp).clip(CircleShape)
                    .border(2.dp, if (selected) Violet600 else Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Violet600))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(plan.label, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(plan.perMonth, fontSize = 11.sp, color = NightTextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(plan.price, fontSize = 16.sp, color = if (selected) Violet400 else Color.White, fontWeight = FontWeight.ExtraBold)
                if (plan.badge != null) {
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (plan.badge == "Популярное") Soft.copy(alpha = 0.20f) else Gold.copy(alpha = 0.20f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(plan.badge, fontSize = 9.sp, color = if (plan.badge == "Популярное") Soft else Gold, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PremiumSuccessScreen — подписка оформлена
// ─────────────────────────────────────────────────────────────────────────────

class PremiumSuccessScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow

        Column(
            Modifier.fillMaxSize().background(NightBackground).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Box(
                Modifier.size(100.dp).clip(CircleShape).background(Brush.linearGradient(ProGradient))
                    .border(3.dp, Gold.copy(alpha = 0.50f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("✨", fontSize = 44.sp) }

            Spacer(Modifier.height(24.dp))
            Text("Добро пожаловать в Pro!", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Все Pro-функции уже доступны. Наслаждайтесь полным опытом EventMap!", fontSize = 14.sp, color = NightTextSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp)

            Spacer(Modifier.height(32.dp))

            // Разблокированные фичи
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(NightSurface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf("✨ Pro-бейдж добавлен на профиль", "🎟 Ранний доступ к билетам активен", "🚫 Реклама отключена", "🎁 7 дней пробного периода").forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✓", fontSize = 14.sp, color = Soft, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(20.dp))
                        Text(item, fontSize = 13.sp, color = Color.White)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(ProGradient)).clickableNoRipple { nav.popUntilRoot() },
                contentAlignment = Alignment.Center,
            ) { Text("Начать исследовать", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.height(40.dp))
        }
    }
}
