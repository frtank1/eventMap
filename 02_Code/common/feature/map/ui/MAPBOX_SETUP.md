# Mapbox SDK — настройка для EventMap (Android, актуальные пути)

> Переход на 2GIS описан в [2GIS_SETUP.md](2GIS_SETUP.md). Этот документ относится
> к прежнему провайдеру `eventmap.mapProvider=mapbox`. Его Android-адаптер теперь
> находится в `src/androidMapbox/kotlin/com/eventmap/map/EventMapView.android.kt`.

> Порядок важен: сначала токены и репозиторий (иначе SDK не скачается и сборка упадёт), потом зависимость, потом вернуть боевую реализацию вместо placeholder. Делать по шагам, после каждого — пересборка.

---

## Шаг 0. Токены (mapbox.com)

1. Регистрация: https://account.mapbox.com/
2. В разделе **Tokens** создать два:
   - **Public** (`pk.*`) — попадёт в приложение.
   - **Secret** (`sk.*`) с правом **Downloads:Read** — только для скачивания SDK при сборке.

---

## Шаг 1. Secret-токен в Gradle (вне репозитория!)

В `~/.gradle/gradle.properties` (НЕ в проекте — чтобы не утёк в git):

```properties
MAPBOX_DOWNLOADS_TOKEN=sk.твой_secret_token
```

---

## Шаг 2. Maven-репозиторий Mapbox

Файл: **`02_Code/settings.gradle.kts`** → блок `dependencyResolutionManagement { repositories { ... } }`.
Добавить туда maven Mapbox (рядом с google()/mavenCentral):

```kotlin
maven {
    url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
    credentials {
        username = "mapbox"
        password = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").get()
    }
    authentication { create<BasicAuthentication>("basic") }
}
```

---

## Шаг 3. Зависимость SDK

Файл: **`02_Code/common/feature/map/ui/build.gradle.kts`** → `androidMain.dependencies`.
Добавить (там уже есть закомментированная подсказка):

```kotlin
androidMain.dependencies {
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.androidx.activity.compose)
    implementation("com.mapbox.maps:android:11.8.0")   // ← добавить
}
```

---

## Шаг 4. Public-токен в приложение

Добавить в **`02_Code/local.properties`** (файл исключён из Git):

```properties
MAPBOX_PUBLIC_TOKEN=pk.твой_public_token
```

Для CI можно передать Gradle property или переменную окружения `MAPBOX_PUBLIC_TOKEN`.
Приоритет: Gradle property → переменная окружения → `local.properties`.
`02_Code/androidApp/build.gradle.kts` генерирует ресурс `mapbox_access_token` при сборке;
не добавляйте токен в отслеживаемые XML-файлы. Без настройки ресурс будет пустым,
и карта Mapbox не сможет загрузиться. Это public-токен приложения, а не отдельный
секретный `MAPBOX_DOWNLOADS_TOKEN` для скачивания SDK.

В **`02_Code/androidApp/src/main/AndroidManifest.xml`** уже есть ссылка на ресурс:

```xml
<meta-data
    android:name="MAPBOX_ACCESS_TOKEN"
    android:value="@string/mapbox_access_token" />
```

---

## Шаг 5. Вернуть боевую реализацию карты

Заменить **placeholder** в
`02_Code/common/feature/map/ui/src/androidMain/kotlin/com/eventmap/map/EventMapView.android.kt`
на реальную реализацию на Mapbox:

```kotlin
package com.eventmap.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class EventMapController(
    private val mapboxMap: MapboxMap,
) {
    private val _viewport = MutableStateFlow(ViewportState(LatLng(43.238949, 76.889709), 13.0))
    actual val viewport: StateFlow<ViewportState> = _viewport.asStateFlow()

    init {
        mapboxMap.subscribeCameraChanged {
            val s = mapboxMap.cameraState
            _viewport.value = ViewportState(
                center = LatLng(s.center.latitude(), s.center.longitude()),
                zoom = s.zoom, bearing = s.bearing, pitch = s.pitch,
            )
        }
    }

    actual fun project(point: LatLng): Offset? {
        val p = mapboxMap.pixelForCoordinate(Point.fromLngLat(point.lon, point.lat))
        return Offset(p.x.toFloat(), p.y.toFloat())
    }

    actual fun unproject(offset: Offset): LatLng {
        val c = mapboxMap.coordinateForPixel(ScreenCoordinate(offset.x.toDouble(), offset.y.toDouble()))
        return LatLng(c.latitude(), c.longitude())
    }

    actual fun setStyle(style: MapStyle) { mapboxMap.loadStyle(MapStyles.urlFor(style)) }

    actual fun animateCamera(target: LatLng, zoom: Double, durationMs: Int) {
        mapboxMap.flyTo(
            cameraOptions { center(Point.fromLngLat(target.lon, target.lat)); zoom(zoom) },
            MapAnimationOptions.mapAnimationOptions { duration(durationMs.toLong()) },
        )
    }
}

@Composable
actual fun EventMap(
    initialCenter: LatLng,
    initialZoom: Double,
    style: MapStyle,
    modifier: Modifier,
    onMapReady: (EventMapController) -> Unit,
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    AndroidView(
        factory = {
            mapView.apply {
                mapboxMap.apply {
                    setCamera(cameraOptions {
                        center(Point.fromLngLat(initialCenter.lon, initialCenter.lat))
                        zoom(initialZoom)
                    })
                    loadStyle(MapStyles.urlFor(style)) { onMapReady(EventMapController(this)) }
                }
            }
        },
        modifier = modifier,
    )

    DisposableEffect(Unit) { onDispose { mapView.onDestroy() } }
}
```

> Сигнатуры Mapbox 11.x могут чуть отличаться по версиям — если компилятор ругнётся на `subscribeCameraChanged`/`loadStyle`, свериться с актуальным API: https://docs.mapbox.com/android/maps/guides/

---

## Шаг 6. Стили (можно позже)

Пока сгодятся дефолтные. `MapStyles.kt` сейчас указывает на `mapbox://styles/eventmap/...` — поменять на свои из Mapbox Studio (https://studio.mapbox.com/) или временно использовать стандартные (`mapbox://styles/mapbox/dark-v11`, `light-v11`).

---

## Шаг 7. Контроль расходов

Mapbox → Account → Statistics → выставить **Usage limit** (free tier щедрый, но лимит + алерт на 80% спасут от сюрпризов).

---

## iOS (когда дойдёшь — позже)

- `~/.netrc`: `machine api.mapbox.com login mapbox password sk.секрет`
- cocoapods в `composeApp/build.gradle.kts` (`pod("MapboxMaps", version = "~> 11.8")`), `MBXAccessToken` в `iosApp/.../Info.plist`.
- Заменить placeholder в `.../iosMain/.../EventMapView.ios.kt` на реализацию через `UIKitView` + `MapboxMaps.MapView`.
