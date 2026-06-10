package com.eventmap.example.ui.models

sealed class ExampleEvent {
    data object Back : ExampleEvent()
    data class Login(val login: String, val password: String) : ExampleEvent()
}
