package com.eventmap.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.eventmap.example.ui.models.ExampleEvent
import com.eventmap.theme.ui_kit.AOButton

class ExampleScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<ExampleViewModel>()
        ExampleContent(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExampleContent(viewModel: IExampleViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // локальный снек экрана слушает ошибки из BaseScreenModel
    LaunchedEffect(Unit) {
        viewModel.errorMessage.collect { snackbar.showSnackbar(it) }
    }

    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Example feature")

            OutlinedTextField(
                value = login,
                onValueChange = { login = it },
                label = { Text("Login") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            if (state is ExampleState.Loading) {
                CircularProgressIndicator()
            }

            AOButton(
                onClick = { viewModel.sendEvent(ExampleEvent.Login(login, password)) },
                enabled = state !is ExampleState.Loading,
                modifier = Modifier.fillMaxWidth(),
                text = "Login"
            )
        }
    }
}
