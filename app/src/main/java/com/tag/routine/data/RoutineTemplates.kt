package com.tag.routine.data

import androidx.annotation.StringRes
import com.tag.routine.R

data class RoutineTemplateItem(
    @StringRes val titleRes: Int,
    val groupName: String,
    val icon: String,
    val colorId: Int,
    val timeMinutes: Int,
)

data class RoutineTemplate(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: String,
    val colorId: Int,
    val items: List<RoutineTemplateItem>,
)

object RoutineTemplates {
    val all: List<RoutineTemplate> = listOf(
        RoutineTemplate(
            id = "morning",
            nameRes = R.string.template_morning_name,
            subtitleRes = R.string.template_morning_sub,
            icon = "☀️",
            colorId = 2,
            items = listOf(
                RoutineTemplateItem(
                    titleRes = R.string.template_morning_water,
                    groupName = Groups.MORNING,
                    icon = "💧",
                    colorId = 4,
                    timeMinutes = 7 * 60,
                ),
                RoutineTemplateItem(
                    titleRes = R.string.template_morning_stretch,
                    groupName = Groups.MORNING,
                    icon = "🧘",
                    colorId = 5,
                    timeMinutes = 7 * 60 + 15,
                ),
                RoutineTemplateItem(
                    titleRes = R.string.template_morning_breakfast,
                    groupName = Groups.MORNING,
                    icon = "☕",
                    colorId = 2,
                    timeMinutes = 7 * 60 + 30,
                ),
                RoutineTemplateItem(
                    titleRes = R.string.template_morning_plan,
                    groupName = Groups.MORNING,
                    icon = "🎯",
                    colorId = 0,
                    timeMinutes = 8 * 60,
                ),
            ),
        ),
        RoutineTemplate(
            id = "study",
            nameRes = R.string.template_study_name,
            subtitleRes = R.string.template_study_sub,
            icon = "📖",
            colorId = 0,
            items = listOf(
                RoutineTemplateItem(
                    titleRes = R.string.template_study_focus,
                    groupName = Groups.AFTERNOON,
                    icon = "📖",
                    colorId = 0,
                    timeMinutes = 14 * 60,
                ),
                RoutineTemplateItem(
                    titleRes = R.string.template_study_notes,
                    groupName = Groups.AFTERNOON,
                    icon = "✍️",
                    colorId = 4,
                    timeMinutes = 15 * 60 + 30,
                ),
                RoutineTemplateItem(
                    titleRes = R.string.template_study_review,
                    groupName = Groups.AFTERNOON,
                    icon = "🧠",
                    colorId = 6,
                    timeMinutes = 16 * 60 + 30,
                ),
            ),
        ),
        RoutineTemplate(
            id = "workout",
            nameRes = R.string.template_workout_name,
            subtitleRes = R.string.template_workout_sub,
            icon = "💪",
            colorId = 3,
            items = listOf(
                RoutineTemplateItem(
                    titleRes = R.string.template_workout_warmup,
                    groupName = Groups.AFTERNOON,
                    icon = "🏃",
                    colorId = 7,
                    timeMinutes = 17 * 60 + 30,
                ),
                RoutineTemplateItem(
                    titleRes = R.string.template_workout_main,
                    groupName = Groups.AFTERNOON,
                    icon = "💪",
                    colorId = 3,
                    timeMinutes = 17 * 60 + 45,
                ),
                RoutineTemplateItem(
                    titleRes = R.string.template_workout_cooldown,
                    groupName = Groups.AFTERNOON,
                    icon = "💧",
                    colorId = 1,
                    timeMinutes = 18 * 60 + 30,
                ),
            ),
        ),
        RoutineTemplate(
            id = "sleep",
            nameRes = R.string.template_sleep_name,
            subtitleRes = R.string.template_sleep_sub,
            icon = "😴",
            colorId = 6,
            items = listOf(
                RoutineTemplateItem(
                    titleRes = R.string.template_sleep_winddown,
                    groupName = Groups.EVENING,
                    icon = "🌿",
                    colorId = 1,
                    timeMinutes = 21 * 60 + 30,
                ),
                RoutineTemplateItem(
                    titleRes = R.string.template_sleep_journal,
                    groupName = Groups.EVENING,
                    icon = "📖",
                    colorId = 6,
                    timeMinutes = 22 * 60,
                ),
                RoutineTemplateItem(
                    titleRes = R.string.template_sleep_lightsout,
                    groupName = Groups.EVENING,
                    icon = "😴",
                    colorId = 0,
                    timeMinutes = 22 * 60 + 30,
                ),
            ),
        ),
    )
}
