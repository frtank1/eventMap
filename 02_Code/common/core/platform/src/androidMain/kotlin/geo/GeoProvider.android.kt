package com.eventmap.core.platform.geo

import PlatformConfiguration
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager

// Без Play Services: LocationManager.lastKnownLocation по всем провайдерам,
// берём самый свежий фикс. Разрешение запрашивает MainActivity при старте.
actual fun createGeoProvider(configuration: PlatformConfiguration): GeoProvider =
    AndroidGeoProvider(configuration.androidContext)

private class AndroidGeoProvider(private val context: Context) : GeoProvider {

    override suspend fun current(): GeoFix? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return try {
            lm.getProviders(true)
                .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.let { GeoFix(it.latitude, it.longitude) }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
