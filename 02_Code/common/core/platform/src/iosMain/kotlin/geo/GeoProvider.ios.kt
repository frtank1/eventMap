package com.eventmap.core.platform.geo

import PlatformConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

// One-shot позиция через CLLocationManager.requestLocation().
// NSLocationWhenInUseUsageDescription — в iosApp/Info.plist.
actual fun createGeoProvider(configuration: PlatformConfiguration): GeoProvider = IosGeoProvider()

private class IosGeoProvider : GeoProvider {

    // CLLocationManager.delegate — weak: держим активные делегаты сами до resume
    private val active = mutableSetOf<NSObject>()

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun current(): GeoFix? = suspendCancellableCoroutine { cont ->
        val manager = CLLocationManager()

        fun finish(fix: GeoFix?, delegate: NSObject) {
            active.remove(delegate)
            manager.delegate = null
            if (cont.isActive) cont.resume(fix)
        }

        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                val loc = didUpdateLocations.lastOrNull() as? CLLocation
                if (loc == null) {
                    finish(null, this)
                } else {
                    val fix = loc.coordinate.useContents { GeoFix(latitude, longitude) }
                    finish(fix, this)
                }
            }

            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                finish(null, this)
            }

            // Первый запуск: ждём ответ на диалог разрешения и только потом просим позицию —
            // иначе requestLocation() падает kCLErrorDenied, пока диалог ещё на экране.
            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                when (manager.authorizationStatus) {
                    kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> finish(null, this)
                    kCLAuthorizationStatusNotDetermined -> Unit // диалог ещё открыт
                    else -> manager.requestLocation()
                }
            }
        }

        val status = manager.authorizationStatus
        if (status == kCLAuthorizationStatusDenied || status == kCLAuthorizationStatusRestricted) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }

        active.add(delegate)
        manager.delegate = delegate
        if (status == kCLAuthorizationStatusNotDetermined) {
            manager.requestWhenInUseAuthorization() // requestLocation — в didChangeAuthorization
        } else {
            manager.requestLocation()
        }

        cont.invokeOnCancellation {
            active.remove(delegate)
            manager.delegate = null
        }
    }
}
