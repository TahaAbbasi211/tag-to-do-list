package com.tag.routine.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Routine icons are persisted by key so the schema stays independent of the
 * icon set. Unknown keys fall back to a neutral glyph.
 */
object RoutineIconMap {

    private val map: Map<String, ImageVector> = mapOf(
        "sunrise" to Icons.Rounded.WbSunny,
        "water" to Icons.Rounded.LocalDrink,
        "run" to Icons.Rounded.DirectionsRun,
        "gym" to Icons.Rounded.FitnessCenter,
        "book" to Icons.Rounded.MenuBook,
        "meditate" to Icons.Rounded.SelfImprovement,
        "journal" to Icons.Rounded.EditNote,
        "walk" to Icons.Rounded.DirectionsWalk,
        "pill" to Icons.Rounded.Medication,
        "coffee" to Icons.Rounded.Coffee,
        "code" to Icons.Rounded.Code,
        "music" to Icons.Rounded.MusicNote,
        "sleep" to Icons.Rounded.Bedtime,
        "broom" to Icons.Rounded.CleaningServices,
        "call" to Icons.Rounded.Call,
        "heart" to Icons.Rounded.Favorite,
        "brain" to Icons.Rounded.Psychology,
        "leaf" to Icons.Rounded.Spa,
        "target" to Icons.Rounded.TrackChanges,
        "spark" to Icons.Rounded.AutoAwesome,
    )

    val keys: List<String> = map.keys.toList()

    fun icon(key: String): ImageVector = map[key] ?: Icons.Rounded.Bolt

    fun label(key: String): String = key.replaceFirstChar { it.uppercase() }
}
