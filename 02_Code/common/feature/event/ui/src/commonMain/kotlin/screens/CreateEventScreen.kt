package com.eventmap.event.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.event.ui.viewmodel.CreateEventViewModel
import com.eventmap.theme.EventMapTheme
import com.eventmap.theme.ui_kit.PrimaryGradientButton
import com.eventmap.theme.ui_kit.SecondaryButton
import com.eventmap.theme.ui_kit.clickableNoRipple

private val categories = listOf("Концерт", "Спорт", "Йога", "Еда", "Детям", "Лекция", "Квиз", "Книжклуб")

class CreateEventScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<CreateEventViewModel>()
        val snackbar = remember { SnackbarHostState() }
        val step by vm.step.collectAsState()
        val form by vm.form.collectAsState()
        val loading by vm.loading.collectAsState()
        val created by vm.created.collectAsState()

        LaunchedEffect(Unit) { vm.errorMessage.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(created) { if (created) nav.pop() }

        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
            Column(Modifier.fillMaxSize().padding(inner).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.clickableNoRipple { if (step == 0) nav.pop() else vm.back() })
                    Spacer(Modifier.width(12.dp))
                    Text("Новое событие", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.height(8.dp))
                StepIndicator(step)
                Spacer(Modifier.height(20.dp))

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when (step) {
                        0 -> StepBasics(form.title, form.description,
                            onTitle = { v -> vm.update { it.copy(title = v) } },
                            onDesc = { v -> vm.update { it.copy(description = v) } })
                        1 -> StepLocation(form.locationName, onName = { v -> vm.update { it.copy(locationName = v) } })
                        else -> StepDetails(
                            form,
                            onDate = { v -> vm.update { it.copy(date = v) } },
                            onTime = { v -> vm.update { it.copy(time = v) } },
                            onCategory = { v -> vm.update { it.copy(category = v) } },
                            onPaid = { v -> vm.update { it.copy(isPaid = v) } },
                            onPrice = { v -> vm.update { it.copy(priceAmount = v) } },
                            onLimit = { v -> vm.update { it.copy(limit = v) } },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                if (step < 2) {
                    PrimaryGradientButton(text = "Далее", onClick = { vm.next() })
                } else {
                    PrimaryGradientButton(text = "Опубликовать", loading = loading, onClick = { vm.publish() })
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            Box(
                Modifier.height(6.dp).then(if (i <= step) Modifier.width(40.dp) else Modifier.width(20.dp))
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (i <= step) EventMapTheme.colors.accent else EventMapTheme.colors.textTertiary),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepBasics(title: String, description: String, onTitle: (String) -> Unit, onDesc: (String) -> Unit) {
    Column {
        FieldLabel("Шаг 1 · Основное")
        OutlinedTextField(value = title, onValueChange = onTitle, label = { Text("Название") }, singleLine = true,
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = description, onValueChange = onDesc, label = { Text("Описание") },
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(140.dp))
        Spacer(Modifier.height(12.dp))
        CoverPlaceholder()
    }
}

@Composable
private fun CoverPlaceholder() {
    Box(
        Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { Text("＋ Добавить обложку", color = EventMapTheme.colors.textSecondary) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepLocation(name: String, onName: (String) -> Unit) {
    Column {
        FieldLabel("Шаг 2 · Локация")
        Box(
            Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp))
                .background(EventMapTheme.colors.mapBg),
            contentAlignment = Alignment.Center,
        ) { Text("🗺️ Выбор точки на карте", color = EventMapTheme.colors.textSecondary) }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = name, onValueChange = onName, label = { Text("Адрес / название места") }, singleLine = true,
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepDetails(
    form: com.eventmap.event.ui.viewmodel.CreateForm,
    onDate: (String) -> Unit,
    onTime: (String) -> Unit,
    onCategory: (String) -> Unit,
    onPaid: (Boolean) -> Unit,
    onPrice: (String) -> Unit,
    onLimit: (String) -> Unit,
) {
    Column {
        FieldLabel("Шаг 3 · Детали")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = form.date, onValueChange = onDate, label = { Text("Дата") }, singleLine = true,
                shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
            OutlinedTextField(value = form.time, onValueChange = onTime, label = { Text("Время") }, singleLine = true,
                shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        FieldLabel("Категория")
        CategoryChips(form.category, onCategory)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Платное событие", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Switch(checked = form.isPaid, onCheckedChange = onPaid)
        }
        if (form.isPaid) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = form.priceAmount, onValueChange = onPrice, label = { Text("Цена, ₸") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = form.limit, onValueChange = onLimit, label = { Text("Лимит участников (необязательно)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CategoryChips(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    val sel = c == selected
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (sel) EventMapTheme.colors.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, if (sel) EventMapTheme.colors.accent else Color.Transparent, RoundedCornerShape(999.dp))
                            .clickableNoRipple { onSelect(c) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(c, style = MaterialTheme.typography.labelLarge,
                            color = if (sel) EventMapTheme.colors.accent else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 12.dp))
}
