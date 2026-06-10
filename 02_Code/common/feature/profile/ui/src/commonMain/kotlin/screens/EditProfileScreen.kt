package com.eventmap.profile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.eventmap.profile.ui.viewmodel.EditProfileViewModel
import com.eventmap.theme.ui_kit.PrimaryGradientButton

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

        LaunchedEffect(Unit) { vm.errorMessage.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(saved) { if (saved) nav.pop() }

        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
            Column(Modifier.fillMaxSize().padding(inner).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.clickable { nav.pop() })
                    Spacer(Modifier.size(12.dp))
                    Text("Редактировать профиль", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("О себе") },
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(120.dp))
                Spacer(Modifier.height(24.dp))
                PrimaryGradientButton(text = "Сохранить", loading = loading, onClick = { vm.save(name, bio) })
            }
        }
    }
}
