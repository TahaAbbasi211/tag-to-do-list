package com.tag.routine.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tag.routine.R
import com.tag.routine.ui.Tab

/**
 * The floating, pill-shaped bottom navigation bar shared by every screen.
 * Powered by [AppBottomNav] with a smooth animated selection pill indicator.
 */
@Composable
fun PillNavBar(
    current: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        BottomNavTabItem(Tab.TODAY, stringResource(R.string.nav_today), Icons.Rounded.Today),
        BottomNavTabItem(Tab.TASKS, stringResource(R.string.nav_tasks), Icons.Rounded.CheckCircle),
        BottomNavTabItem(Tab.FOCUS, stringResource(R.string.nav_focus), Icons.Rounded.Timer),
        BottomNavTabItem(Tab.INSIGHTS, stringResource(R.string.nav_insights), Icons.Rounded.BarChart),
        BottomNavTabItem(Tab.SETTINGS, stringResource(R.string.nav_settings), Icons.Rounded.Settings),
    )

    AppBottomNav(
        items = items,
        current = current,
        onSelect = onSelect,
        modifier = modifier,
    )
}
