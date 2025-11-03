package de.mimuc.senseeverything.activity.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val Black = Color(0xFF000000)

/**
 * Colors for study status indicators
 */
object StudyStatusColors {
    val Running = Color.hsl(80f, 1f, 0.33f, 1f)  // Green
    val Warning = Color.hsl(37f, 1f, 0.50f, 1f)   // Orange
    val Stopped = Color.hsl(0f, 1f, 0.41f, 1f)   // Red
    val Ended = Color.LightGray                   // Gray
}