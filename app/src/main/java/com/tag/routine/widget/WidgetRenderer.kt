package com.tag.routine.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.tag.routine.MainActivity
import com.tag.routine.R
import com.tag.routine.data.Dates
import java.util.Locale

/**
 * Draws a [WidgetState] into RemoteViews.
 *
 * Everything user-visible goes through [WidgetLocale.localizedContext] and
 * [Dates.formatDigits] / [Dates.formatPercent], so the widget follows the language and
 * digit style chosen inside the app, not the device language.
 */
object WidgetRenderer {

    /** Below this width (dp) the launcher gets the compact layout. */
    private const val WIDE_MIN_WIDTH_DP = 250

    private data class RowSlot(
        val containerId: Int,
        val iconId: Int,
        val titleId: Int,
        val timeId: Int,
    )

    private val rowSlots = listOf(
        RowSlot(
            R.id.widget_row_1,
            R.id.widget_item_icon_1,
            R.id.widget_item_title_1,
            R.id.widget_item_time_1,
        ),
        RowSlot(
            R.id.widget_row_2,
            R.id.widget_item_icon_2,
            R.id.widget_item_title_2,
            R.id.widget_item_time_2,
        ),
        RowSlot(
            R.id.widget_row_3,
            R.id.widget_item_icon_3,
            R.id.widget_item_title_3,
            R.id.widget_item_time_3,
        ),
    )

    /** Drawable for a persisted routine icon key (emoji or legacy v1/v2 key). */
    internal fun iconRes(key: String): Int = when (key.trim()) {
        "sunrise", "☀️", "☀" -> R.drawable.ic_widget_wb_sunny
        "water", "💧" -> R.drawable.ic_widget_local_drink
        "run", "🏃" -> R.drawable.ic_widget_directions_run
        "gym", "💪" -> R.drawable.ic_widget_fitness_center
        "book", "📖" -> R.drawable.ic_widget_menu_book
        "meditate", "🧘" -> R.drawable.ic_widget_self_improvement
        "journal", "✍️", "✍" -> R.drawable.ic_widget_edit_note
        "walk", "🚶" -> R.drawable.ic_widget_directions_walk
        "pill", "💊" -> R.drawable.ic_widget_medication
        "coffee", "☕" -> R.drawable.ic_widget_coffee
        "code", "💻" -> R.drawable.ic_widget_code
        "music", "🎵" -> R.drawable.ic_widget_music_note
        "sleep", "😴" -> R.drawable.ic_widget_bedtime
        "broom", "🧹" -> R.drawable.ic_widget_cleaning_services
        "call", "📞" -> R.drawable.ic_widget_call
        "heart", "❤️", "❤" -> R.drawable.ic_widget_favorite
        "brain", "🧠" -> R.drawable.ic_widget_psychology
        "leaf", "🌿" -> R.drawable.ic_widget_spa
        "target", "🎯" -> R.drawable.ic_widget_track_changes
        "spark", "✨" -> R.drawable.ic_widget_auto_awesome
        else -> R.drawable.ic_widget_bolt
    }

    /**
     * Builds the RemoteViews for one widget instance.
     *
     * @param widthDp the instance's current width, used to pick the compact or wide layout.
     */
    fun render(
        context: Context,
        state: WidgetState,
        settings: com.tag.routine.data.AppSettings,
        widthDp: Int,
    ): RemoteViews {
        val localized = WidgetLocale.localizedContext(context, settings)
        val locale = WidgetLocale.localeFor(settings)
        val rtl = WidgetLocale.isRtl(locale)
        val wide = widthDp >= WIDE_MIN_WIDTH_DP

        val layout = if (wide) R.layout.widget_today_wide else R.layout.widget_today_small
        val views = RemoteViews(context.packageName, layout)

        views.setInt(
            R.id.widget_root,
            "setLayoutDirection",
            if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
        )

        // Header
        views.setTextViewText(R.id.widget_title, localized.getString(R.string.widget_title_today))
        views.setViewVisibility(
            R.id.widget_status_icon,
            if (state.mode == WidgetMode.COMPLETE) View.VISIBLE else View.GONE,
        )

        // Progress block. An empty day shows a neutral message instead of a "0%" failure.
        val showPercent = state.mode != WidgetMode.EMPTY
        views.setViewVisibility(R.id.widget_percent, if (showPercent) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_progress, if (showPercent) View.VISIBLE else View.GONE)

        if (showPercent) {
            views.setTextViewText(
                R.id.widget_percent,
                Dates.formatPercent(state.percent, locale),
            )
            views.setProgressBar(R.id.widget_progress, 100, state.percent, false)
            views.setTextViewText(
                R.id.widget_counter,
                Dates.formatDigits(
                    localized.getString(R.string.widget_counter, state.completed, state.total),
                    locale,
                ),
            )
        } else {
            views.setTextViewText(
                R.id.widget_counter,
                localized.getString(R.string.widget_empty_title),
            )
        }

        if (wide) {
            bindItems(views, localized, locale, state)
        }

        // Tapping anywhere opens the app. Checked items are intentionally not toggleable
        // from the widget: a stale widget could then disagree with the database.
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))

        return views
    }

    private fun bindItems(
        views: RemoteViews,
        localized: Context,
        locale: Locale,
        state: WidgetState,
    ) {
        if (state.mode == WidgetMode.EMPTY) {
            // Nothing scheduled: one calm message, no empty rows.
            views.setViewVisibility(R.id.widget_items, View.VISIBLE)
            rowSlots.forEach { views.setViewVisibility(it.containerId, View.GONE) }
            views.setViewVisibility(R.id.widget_empty_message, View.VISIBLE)
            views.setTextViewText(
                R.id.widget_empty_message,
                localized.getString(R.string.widget_empty_message),
            )
            return
        }

        if (state.mode == WidgetMode.COMPLETE) {
            views.setViewVisibility(R.id.widget_items, View.VISIBLE)
            rowSlots.forEach { views.setViewVisibility(it.containerId, View.GONE) }
            views.setViewVisibility(R.id.widget_empty_message, View.VISIBLE)
            views.setTextViewText(
                R.id.widget_empty_message,
                localized.getString(R.string.widget_all_done_message),
            )
            return
        }

        views.setViewVisibility(R.id.widget_empty_message, View.GONE)
        views.setViewVisibility(R.id.widget_items, View.VISIBLE)

        rowSlots.forEachIndexed { index, slot ->
            val item = state.nextItems.getOrNull(index)
            if (item == null) {
                views.setViewVisibility(slot.containerId, View.GONE)
            } else {
                views.setViewVisibility(slot.containerId, View.VISIBLE)
                views.setImageViewResource(slot.iconId, iconRes(item.icon))
                views.setContentDescription(slot.iconId, item.title)
                views.setTextViewText(slot.titleId, item.title)
                val time = item.timeMinutes
                if (time == null) {
                    views.setViewVisibility(slot.timeId, View.GONE)
                } else {
                    views.setViewVisibility(slot.timeId, View.VISIBLE)
                    views.setTextViewText(slot.timeId, Dates.timeLabel(time, locale))
                }
            }
        }
    }

    /** PendingIntent that opens the app's main screen. */
    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * The widget instances currently on the home screen, used to refresh them all.
     * Returns an empty array when none are placed, which is the common case.
     */
    fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            ?.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            ?: IntArray(0)
}
