package com.eventmap.core.utils

import kotlinx.datetime.LocalDateTime

fun LocalDateTime.toDisplayDate(): String {
    val day = dayOfMonth.toString().padStart(2, '0')
    val month = monthNumber.toString().padStart(2, '0')
    val year = year.toString().takeLast(2)
    return "$day.$month.$year"
}

fun String.isoToDisplayDate(): String {
    return try {
        LocalDateTime.parse(this).toDisplayDate()
    } catch (e: Exception) {
        this
    }
}

fun LocalDateTime.toDisplayTime(): String {
    val hour = hour.toString().padStart(2, '0')
    val minute = minute.toString().padStart(2, '0')
    val second = second.toString().padStart(2, '0')

    return "$hour:$minute:$second"
}

fun String.isoToDisplayTime(): String {
    return try {
        LocalDateTime.parse(this).toDisplayTime()
    } catch (e: Exception) {
        this
    }
}
