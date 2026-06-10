package com.eventmap.example.ui.models

sealed class NavigationEvent {
    private var handled: Boolean = false

    fun getValue(): NavigationEvent {
        if (handled) return Default
        handled = true
        return this
    }

    data object Default : NavigationEvent()
}
