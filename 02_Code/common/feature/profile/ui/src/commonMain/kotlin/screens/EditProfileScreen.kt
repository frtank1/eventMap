package com.eventmap.profile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.profile.ui.viewmodel.EditProfileViewModel
import com.eventmap.theme.BrandGradient
import com.eventmap.theme.NightBackground
import com.eventmap.theme.NightSurface
import com.eventmap.theme.NightTextSecondary
import com.eventmap.theme.Violet400
import com.eventmap.theme.Violet600
import com.eventmap.theme.ui_kit.clickableNoRipple

// =====================================================================
//  H3 — Редактирование профиля
// =====================================================================

data class EditProfileScreen(val initialName: String, val initialBio: String) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val vm = koinScreenModel<EditProfileViewModel>()
        val snackbar = remember { SnackbarHostState() }
        val loading by vm.loading.collectAsState()
        val saved by vm.saved.collectAsState()

        var name by remember { mutableStateOf(initialName) }
        var bio by remember { mutableStateOf(initialBio) }
        var city by remember { mutableStateOf("") }

        LaunchedEffect(Unit) { vm.errorMessage.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(saved) { if (saved) nav.pop() }

        val fieldColors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = NightSurface,
            focusedContainerColor = NightSurface,
            unfocusedTextColor = Color.White,
            focusedTextColor = Color.White,
            unfocusedLabelColor = NightTextSecondary,
            focusedLabelColor = Violet400,
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            focusedBorderColor = Violet600,
            cursorColor = Violet600,
        )

        Column(
            Modifier
                .fillMaxSize()
                .background(NightBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // Top bar
            Row(
                Modifier.padding(top = 56.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickableNoRipple { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text("Редактировать профиль", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            // Аватар с кнопкой камеры
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(BrandGradient))
                        .border(2.dp, Brush.linearGradient(BrandGradient), CircleShape)
                        .clickableNoRipple { /* открыть галерею */ },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name.take(1).uppercase(), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 58.dp, start = 58.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Violet600),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = "Сменить фото", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(Modifier.height(28.dp))
            FormLabel("ИМЯ")
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(14.dp))
            FormLabel("О СЕБЕ")
            OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("О себе") }, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth().height(110.dp))

            Spacer(Modifier.height(14.dp))
            FormLabel("ГОРОД")
            OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("Астана") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(BrandGradient))
                    .clickableNoRipple { if (!loading) vm.save(name, bio) },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (loading) "Сохраняем…" else "Сохранить", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(Modifier.height(28.dp))
            SnackbarHost(snackbar)
        }
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(text, color = NightTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 8.dp))
}
