package com.eventmap.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Local vector assets: consistent 24-unit outline icons, no emoji or network font. */
internal object OutlineIcons {
    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ).build()

    val Map = icon("Map", "M3 5L9 3L15 5L21 3V19L15 21L9 19L3 21Z M9 3V19 M15 5V21")
    val Video = icon("Video", "M3 9H21V19Q21 21 19 21H5Q3 21 3 19Z M3 9L2 5L20 1L21 5Z M7 4L10 7 M14 2.5L17 5.5")
    val Compass = icon("Compass", "M21 12A9 9 0 1 1 3 12A9 9 0 1 1 21 12 M15.5 8.5L13.5 13.5L8.5 15.5L10.5 10.5Z")
    val Profile = icon("Profile", "M17 7A5 5 0 1 1 7 7A5 5 0 1 1 17 7 M3 22V20A9 9 0 0 1 21 20V22")
    val Bell = icon("Bell", "M18 8A6 6 0 0 0 6 8C6 15 3 16 3 17H21C21 16 18 15 18 8 M10 21Q12 23 14 21")
    val Plus = icon("Plus", "M12 4V20 M4 12H20")
    val Heart = icon("Heart", "M20.5 4.5C18 2 14 3 12 6C10 3 6 2 3.5 4.5C0 8 3 12 12 21C21 12 24 8 20.5 4.5Z")
    val Comment = icon("Comment", "M21 11.5A9 9 0 0 1 7 19L3 21L4.5 16A9 9 0 1 1 21 11.5Z")
    val Share = icon("Share", "M22 2L15 22L10 14L2 9Z M10 14L22 2")
    val Play = icon("Play", "M8 4L21 12L8 20Z")
    val Pause = icon("Pause", "M8 4V20 M16 4V20")
    val Back = icon("Back", "M19 12H5 M12 5L5 12L12 19")
    val Chevron = icon("Chevron", "M9 5L16 12L9 19")
    val Refresh = icon("Refresh", "M20 7A9 9 0 1 0 21 15 M20 2V8H14")
    val Volume = icon("Volume", "M3 9H7L12 5V19L7 15H3Z M16 8Q20 12 16 16 M19 4Q25 12 19 20")
    val Muted = icon("Muted", "M3 9H7L12 5V19L7 15H3Z M17 9L23 15 M23 9L17 15")
    val Settings = icon("Settings", "M4 7H20 M4 17H20 M9 4V10 M15 14V20")
    val Calendar = icon("Calendar", "M5 4H19Q21 4 21 6V20Q21 22 19 22H5Q3 22 3 20V6Q3 4 5 4 M3 10H21 M8 2V6 M16 2V6")
    val Place = icon("Place", "M20 9C20 15 12 22 12 22C12 22 4 15 4 9A8 8 0 0 1 20 9 M15 9A3 3 0 1 1 9 9A3 3 0 1 1 15 9")
    val Search = icon("Search", "M17 10A7 7 0 1 1 3 10A7 7 0 1 1 17 10 M15 15L22 22")
    val Close = icon("Close", "M5 5L19 19 M19 5L5 19")
}
