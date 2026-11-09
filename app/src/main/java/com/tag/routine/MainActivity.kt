package com.tag.routine

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tag.routine.data.RoutineEntity
import com.tag.routine.data.TaskEntity
import com.tag.routine.ui.AppViewModel
import com.tag.routine.ui.Tab
import com.tag.routine.ui.UiState
import com.tag.routine.ui.components.PillNavBar
import com.tag.routine.ui.editors.RoutineEditorSheet
import com.tag.routine.ui.editors.TaskEditorSheet
import com.tag.routine.ui.i18n.LocalizedApp
import com.tag.routine.ui.screens.FocusScreen
import com.tag.routine.ui.screens.InsightsScreen
import com.tag.routine.ui.screens.LanguageScreen
import com.tag.routine.ui.screens.OnboardingScreen
import com.tag.routine.ui.screens.SettingsScreen
import com.tag.routine.ui.screens.TasksScreen
import com.tag.routine.ui.screens.TodayScreen
import com.tag.routine.ui.theme.TagTheme
import kotlinx.coroutines.launch

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppViewModel(container) as T
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        AppViewModelFactory((application as TagApp).container)
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the splash theme is consumed correctly.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val state by viewModel.state.collectAsState()
            LocalizedApp(languageTag = state.settings.language) {
                TagTheme(
                    themeMode = state.settings.themeMode,
                    paletteKey = state.settings.themePalette,
                ) {
                    // First launch: ask for a name before showing the app itself.
                    // The flag lives in the settings store, so this only ever shows once.
                    if (!state.loaded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                        )
                    } else if (!state.settings.onboarded) {
                        OnboardingScreen(
                            onDone = viewModel::completeOnboarding,
                            onSkip = viewModel::skipOnboarding,
                        )
                    } else {
                        TagRoot(state = state, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun TagRoot(state: UiState, viewModel: AppViewModel) {
    var tab by remember { mutableStateOf(Tab.TODAY) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var creatingTask by remember { mutableStateOf(false) }
    var editingRoutine by remember { mutableStateOf<RoutineEntity?>(null) }
    var creatingRoutine by remember { mutableStateOf(false) }
    // Settings owns a small sub-page stack: null means the settings list.
    var showLanguagePage by remember { mutableStateOf(false) }

    val focus by viewModel.focus.collectAsState()

    val query by viewModel.query.collectAsState()
    val filter by viewModel.activeFilter.collectAsState()
    val tagFilter by viewModel.activeTag.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // System back closes the Settings sub-page before it leaves the app.
    BackHandler(enabled = showLanguagePage && tab == Tab.SETTINGS) {
        showLanguagePage = false
    }

    val undoLabel = stringResource(R.string.action_undo)
    val undoRoutineLabel = stringResource(R.string.undo_routine_deleted)
    val undoTaskLabel = stringResource(R.string.undo_task_deleted)
    val undoTagLabel = stringResource(R.string.undo_tag_deleted)

    // Undo for destructive actions: the pending action is replayed only if the user
    // taps Undo before the snackbar times out.
    fun offerUndo(message: String, undo: () -> Unit) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    // The language page is a sub-page of Settings and takes over the
                    // content area, so switching tabs clears it.
                    if (showLanguagePage && tab == Tab.SETTINGS) {
                        LanguageScreen(
                            current = state.settings.language,
                            onSelect = viewModel::setLanguage,
                            onBack = { showLanguagePage = false },
                        )
                    } else {
                        when (tab) {
                            Tab.TODAY -> TodayScreen(
                                state = state,
                                onToggleRoutine = viewModel::toggleRoutine,
                                onSelectDate = viewModel::selectDate,
                                onAddRoutine = { creatingRoutine = true },
                                onEditRoutine = { editingRoutine = it },
                                onDeleteRoutine = { routine ->
                                    viewModel.deleteRoutine(routine.id)
                                    offerUndo(undoRoutineLabel) { viewModel.restoreRoutine(routine) }
                                },
                                // The Today hero pill jumps to the dedicated Focus
                                // section, which owns duration selection and history.
                                onStartFocus = { tab = Tab.FOCUS },
                                onMoveRoutine = viewModel::moveRoutine,
                            )

                            Tab.TASKS -> TasksScreen(
                                state = state,
                                query = query,
                                filter = filter,
                                tagFilter = tagFilter,
                                onSearch = viewModel::setSearch,
                                onFilter = viewModel::setFilter,
                                onTagFilter = viewModel::setTagFilter,
                                onToggleTask = viewModel::toggleTask,
                                onDeleteTask = { id ->
                                    val removed = state.tasks.firstOrNull { it.task.id == id }?.task
                                    viewModel.deleteTask(id)
                                    if (removed != null) {
                                        offerUndo(undoTaskLabel) { viewModel.restoreTask(removed) }
                                    }
                                },
                                onEditTask = { editingTask = it },
                                onAddTask = { creatingTask = true },
                            )

                            Tab.FOCUS -> FocusScreen(
                                routines = state.allRoutines,
                                focus = focus,
                                stats = state.focusStats,
                                defaultMinutes = state.settings.focusMinutes,
                                onStart = viewModel::startFocus,
                                onPause = viewModel::pauseFocus,
                                onResume = viewModel::resumeFocus,
                                onFinish = viewModel::finishFocus,
                            )

                            Tab.INSIGHTS -> InsightsScreen(state = state)

                            Tab.SETTINGS -> SettingsScreen(
                                state = state,
                                onThemeMode = viewModel::setThemeMode,
                                onThemePalette = viewModel::setTheme,
                                onNotifications = viewModel::setNotificationsEnabled,
                                onDigest = viewModel::setDigestEnabled,
                                onDigestMinutes = viewModel::setDigestMinutes,
                                onExport = { viewModel.exportBackup() },
                                onImported = viewModel::importBackup,
                                onReset = viewModel::resetProgress,
                                onAddTag = { name -> viewModel.addTag(name) },
                                onDeleteTag = { id ->
                                    val removed = state.tags.firstOrNull { it.id == id }
                                    viewModel.deleteTag(id)
                                    if (removed != null) {
                                        offerUndo(undoTagLabel) { viewModel.restoreTag(removed) }
                                    }
                                },
                                onSetName = viewModel::setUserName,
                                onFocusMinutes = viewModel::setFocusMinutes,
                                onOpenLanguages = { showLanguagePage = true },
                            )
                        }
                    }
                }
                PillNavBar(
                    current = tab,
                    onSelect = {
                        // Leaving Settings always drops its sub-page, so returning to
                        // Settings later starts on the list rather than a stale page.
                        if (it != Tab.SETTINGS) showLanguagePage = false
                        tab = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
            )
        }
    }

    if (creatingTask || editingTask != null) {
        TaskEditorSheet(
            existing = editingTask,
            tags = state.tags,
            onDismiss = {
                creatingTask = false
                editingTask = null
            },
            onSave = { title, note, tagId, priority, dueDate, dueTime, remindAt ->
                viewModel.saveTask(
                    existing = editingTask,
                    title = title,
                    note = note,
                    tagId = tagId,
                    priority = priority,
                    dueDate = dueDate,
                    dueTimeMinutes = dueTime,
                    remindAt = remindAt,
                )
            },
        )
    }

    if (creatingRoutine || editingRoutine != null) {
        RoutineEditorSheet(
            existing = editingRoutine,
            onDismiss = {
                creatingRoutine = false
                editingRoutine = null
            },
            onSave = { title, group, icon, timeMinutes, daysMask, reminder, colorHex ->
                viewModel.saveRoutine(
                    existing = editingRoutine,
                    title = title,
                    group = group,
                    icon = icon,
                    timeMinutes = timeMinutes,
                    daysMask = daysMask,
                    reminder = reminder,
                    colorHex = colorHex,
                )
            },
        )
    }
}

