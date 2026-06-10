package com.eventmap.auth.ui

import cafe.adriel.voyager.core.screen.Screen

/**
 * Поставщик главного экрана приложения. Реализуется в composeApp,
 * чтобы auth.ui не зависел от других фич. Регистрируется в Koin.
 */
fun interface HomeScreenProvider {
    fun home(): Screen
}
