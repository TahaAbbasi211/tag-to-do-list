package com.tag.routine.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.InsertChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.InsertChart
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
 * Powered by [AppBottomNav] with an RTL-aware spring-sliding pill and outline-to-filled icons.
 */
@Composable
fun PillNavBar(
    current: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        BottomNavTabItem(
            value = Tab.TODAY,
            label = stringResource(R.string.nav_today),
            icon = Icons.Outlined.Today,
            selectedIcon = Icons.Rounded.Today,
        ),
        BottomNavTabItem(
            value = Tab.TASKS,
            label = stringResource(R.string.nav_tasks),
            icon = Icons.Outlined.CheckCircle,
            selectedIcon = Icons.Rounded.CheckCircle,
        ),
        BottomNavTabItem(
            value = Tab.FOCUS,
            label = stringResource(R.string.nav_focus),
            icon = Icons.Outlined.Timer,
            selectedIcon = Icons.Rounded.Timer,
        ),
        BottomNavTabItem(
            value = Tab.INSIGHTS,
            label = stringResource(R.string.nav_insights),
            icon = Icons.Outlined.InsertChart,
            selectedIcon = Icons.Rounded.InsertChart,
        ),
        BottomNavTabItem(
            value = Tab.SETTINGS,
            label = stringResource(R.string.nav_settings),
            icon = Icons.Outlined.Settings,
            selectedIcon = Icons.Rounded.Settings,
        ),
    )

    AppBottomNav(
        items = items,
        current = current,
        onSelect = onSelect,
        modifier = modifier,
    )
}
