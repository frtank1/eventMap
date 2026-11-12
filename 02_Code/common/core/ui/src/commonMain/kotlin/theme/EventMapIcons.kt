package com.eventmap.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Единый контурный набор для основного интерфейса EventMap.
 * Emoji остаются только контентом (реакции и категории), но не кнопками навигации.
 */
object EventMapIcons {
    val Map: ImageVector = OutlineIcons.Map
    val Reels: ImageVector = OutlineIcons.Compass
    val Video: ImageVector = OutlineIcons.Video
    val Profile: ImageVector = OutlineIcons.Profile
    val Flame: ImageVector = Icons.Rounded.LocalFireDepartment
    val Bell: ImageVector = OutlineIcons.Bell
    val Plus: ImageVector = OutlineIcons.Plus
    val Heart: ImageVector = OutlineIcons.Heart
    val Comment: ImageVector = OutlineIcons.Comment
    val Share: ImageVector = OutlineIcons.Share
    val Play: ImageVector = OutlineIcons.Play
    val Pause: ImageVector = OutlineIcons.Pause
    val Back: ImageVector = OutlineIcons.Back
    val Chevron: ImageVector = OutlineIcons.Chevron
    val Refresh: ImageVector = OutlineIcons.Refresh
    val Volume: ImageVector = OutlineIcons.Volume
    val Muted: ImageVector = OutlineIcons.Muted
    val Settings: ImageVector = OutlineIcons.Settings
    val Food: ImageVector = Icons.Rounded.Restaurant
    val Leaf: ImageVector = Icons.Rounded.Spa

    val Pulse: ImageVector = Icons.Rounded.Bolt
    val Flow: ImageVector = Icons.Rounded.Water
    val Heat: ImageVector = Icons.Rounded.LocalFireDepartment
    val Fog: ImageVector = Icons.Rounded.BlurOn
    val People: ImageVector = Icons.Rounded.Groups
    val Compass: ImageVector = OutlineIcons.Compass
    val Day: ImageVector = Icons.Rounded.WbSunny
    val Night: ImageVector = Icons.Rounded.DarkMode
    val Calendar: ImageVector = OutlineIcons.Calendar
    val Camera: ImageVector = Icons.Rounded.PhotoCamera
    val Edit: ImageVector = Icons.Rounded.Edit
    val Delete: ImageVector = Icons.Rounded.Delete
    val Place: ImageVector = OutlineIcons.Place
    val Search: ImageVector = OutlineIcons.Search
    val Close: ImageVector = OutlineIcons.Close
    val Star: ImageVector = Icons.Rounded.Star
}

@Composable
fun GradientIconTile(
    icon: ImageVector,
    gradient: Brush,
    modifier: Modifier = Modifier.size(56.dp),
) {
    Box(
        modifier.clip(RoundedCornerShape(percent = 30)).background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize(0.62f))
    }
}
