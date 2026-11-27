package com.tag.routine

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tag.routine.data.BackupMode
import com.tag.routine.data.Dates
import com.tag.routine.data.RoutineEntity
import com.tag.routine.data.TaskEntity
import com.tag.routine.notifications.NotificationPolicy
import com.tag.routine.ui.AppViewModel
import com.tag.routine.ui.Tab
import com.tag.routine.ui.UiState
import com.tag.routine.ui.components.AppButton
import com.tag.routine.ui.components.AppButtonVariant
import com.tag.routine.ui.components.PillNavBar
import com.tag.routine.ui.editors.RoutineEditorSheet
import com.tag.routine.ui.editors.TaskEditorSheet
import com.tag.routine.ui.editors.TemplatePickerSheet
import com.tag.routine.ui.i18n.LocalizedAlertDialog
import com.tag.routine.ui.i18n.LocalizedApp
import com.tag.routine.ui.motion.AnimationLevel
import com.tag.routine.ui.motion.FocusMotionLogic
import com.tag.routine.ui.motion.LocalAnimationLevel
import com.tag.routine.ui.motion.LocalMotionRevealRegistry
import com.tag.routine.ui.motion.MotionRevealRegistry
import com.tag.routine.ui.motion.TagMotion
import com.tag.routine.ui.screens.FocusScreen
import com.tag.routine.ui.screens.InsightsScreen
import com.tag.routine.ui.screens.LanguageScreen
import com.tag.routine.ui.screens.OnboardingScreen
import com.tag.routine.ui.screens.SettingsScreen
import com.tag.routine.ui.screens.TasksScreen
import com.tag.routine.ui.screens.TodayScreen
import com.tag.routine.ui.theme.TagTheme
import com.tag.routine.widget.WidgetRefresh
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
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.rescheduleReminders()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the splash theme is consumed correctly.
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !viewModel.state.value.loaded }
        installLivingSplashExit(splashScreen)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state by viewModel.state.collectAsState()
            LocalizedApp(languageTag = state.settings.language) {
                TagTheme(
                    themeMode = state.settings.themeMode,
                    paletteKey = state.settings.themePalette,
                    dynamicColor = state.settings.dynamicColor,
                    animationSetting = state.settings.animationSetting,
                    hapticsEnabled = state.settings.hapticsEnabled,
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
                            currentLanguage = state.settings.language,
                            onSelectLanguage = viewModel::setLanguage,
                            onDone = { name ->
                                viewModel.completeOnboarding(name)
                            },
                            onSkip = {
                                viewModel.skipOnboarding()
                            },
                        )
                    } else {
                        TagRoot(
                            state = state,
                            viewModel = viewModel,
                            onRequestNotificationPermission = {
                                requestNotificationPermissionIfNeeded(contextualTrigger = true)
                            },
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded(contextualTrigger: Boolean = true) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (NotificationPolicy.shouldRequestRuntimePermission(Build.VERSION.SDK_INT, granted, contextualTrigger)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (granted) {
            viewModel.rescheduleReminders()
        }
    }

    override fun onStart() {
        super.onStart()
        // Ensures the calendar date and widgets catch up on midnight or clock boundaries.
        viewModel.refreshDateIfNeeded()
        WidgetRefresh.request(applicationContext)
    }

    /**
     * Smoothly fades the splash backdrop and scales/lifts the launcher icon upward toward
     * the Today hero while honoring system reduced-motion settings and API guards.
     */
    private fun installLivingSplashExit(splashScreen: SplashScreen) {
        splashScreen.setOnExitAnimationListener { splashProvider ->
            val animatorsEnabled = runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
            val durationScale = runCatching {
                Settings.Global.getFloat(
                    contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            }.getOrDefault(1f)
            if (!animatorsEnabled || durationScale <= 0f) {
                runCatching { splashProvider.remove() }
                return@setOnExitAnimationListener
            }

            runCatching {
                val rootView = splashProvider.view
                val iconView = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching { splashProvider.iconView }.getOrNull()
                } else {
                    null
                }
                val interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
                val heroLiftPx = -resources.displayMetrics.density * 36f
                val animators = mutableListOf<Animator>(
                    ObjectAnimator.ofFloat(rootView, View.ALPHA, 1f, 0f),
                )
                if (iconView != null) {
                    animators += ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 1.18f)
                    animators += ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 1.18f)
                    animators += ObjectAnimator.ofFloat(iconView, View.TRANSLATION_Y, 0f, heroLiftPx)
                    animators += ObjectAnimator.ofFloat(iconView, View.ALPHA, 1f, 0f)
                }
                AnimatorSet().apply {
                    playTogether(animators)
                    duration = TagMotion.DURATION_STANDARD.toLong()
                    setInterpolator(interpolator)
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                runCatching { splashProvider.remove() }
                            }

                            override fun onAnimationCancel(animation: Animator) {
                                runCatching { splashProvider.remove() }
                            }
                        },
                    )
                    start()
                }
            }.onFailure {
                runCatching { splashProvider.remove() }
            }
        }
    }
}

private data class RootDestination(
    val tab: Tab,
    val languageSubPage: Boolean,
)

@Composable
private fun TagRoot(
    state: UiState,
    viewModel: AppViewModel,
    onRequestNotificationPermission: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.TODAY) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var creatingTask by remember { mutableStateOf(false) }
    var editingRoutine by remember { mutableStateOf<RoutineEntity?>(null) }
    var creatingRoutine by remember { mutableStateOf(false) }
    var pendingDeleteRecurring by remember { mutableStateOf<RoutineEntity?>(null) }
    var pendingSwitchFocusRoutine by remember { mutableStateOf<RoutineEntity?>(null) }
    var showTemplatesSheet by remember { mutableStateOf(false) }
    // Settings owns a small sub-page stack: null means the settings list.
    var showLanguagePage by remember { mutableStateOf(false) }
    var focusCalmMode by remember { mutableStateOf(false) }

    val focus by viewModel.focus.collectAsState()
    val backupState by viewModel.backupUiState.collectAsState()

    LaunchedEffect(tab, focus.active) {
        if (tab != Tab.FOCUS || !focus.active) {
            focusCalmMode = false
        }
    }

    BackHandler(enabled = focusCalmMode && tab == Tab.FOCUS) {
        focusCalmMode = false
    }

    val query by viewModel.query.collectAsState()
    val filter by viewModel.activeFilter.collectAsState()
    val tagFilter by viewModel.activeTag.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Copy actions report their outcome through one shared snackbar.
    val copyResult by viewModel.copyResult.collectAsState()
    val copyContext = LocalContext.current
    val appLocale = LocalConfiguration.current.locales[0] ?: java.util.Locale.getDefault()
    LaunchedEffect(copyResult?.eventId) {
        val feedback = copyResult ?: return@LaunchedEffect
        viewModel.consumeCopyResult()
        val message = when {
            feedback.count == 1 -> copyContext.getString(R.string.copy_done_one, feedback.count)
            feedback.count > 1 -> copyContext.getString(R.string.copy_done_count, feedback.count)
            feedback.fromYesterday -> copyContext.getString(R.string.copy_nothing_to_copy)
            else -> copyContext.getString(R.string.copy_nothing_done)
        }
        val formatted = Dates.formatDigits(message, appLocale)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(formatted)
        }
    }

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

    val entryLevel = LocalAnimationLevel.current
    val rootEnter = remember { Animatable(if (entryLevel.isOff) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (entryLevel.isOff) {
            rootEnter.snapTo(1f)
        } else {
            rootEnter.animateTo(
                targetValue = 1f,
                animationSpec = if (entryLevel == AnimationLevel.FULL) {
                    TagMotion.emphasizedTween()
                } else {
                    TagMotion.quickTween()
                },
            )
        }
    }

    val revealRegistry = remember { MotionRevealRegistry() }

    CompositionLocalProvider(LocalMotionRevealRegistry provides revealRegistry) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = rootEnter.value.coerceIn(0f, 1f)
                    alpha = p
                    if (entryLevel == AnimationLevel.FULL) {
                        val s = 0.975f + 0.025f * p
                        scaleX = s
                        scaleY = s
                    }
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    // The language page is a sub-page of Settings and takes over the
                    // content area, so switching tabs clears it.
                    val level = LocalAnimationLevel.current
                    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                    val destination = RootDestination(
                        tab = tab,
                        languageSubPage = showLanguagePage && tab == Tab.SETTINGS,
                    )
                    AnimatedContent(
                        targetState = destination,
                        transitionSpec = {
                            if (level.isOff) {
                                ContentTransform(
                                    targetContentEnter = fadeIn(TagMotion.instantTween(0)),
                                    initialContentExit = fadeOut(TagMotion.instantTween(0)),
                                )
                            } else if (level == AnimationLevel.REDUCED) {
                                ContentTransform(
                                    targetContentEnter = fadeIn(TagMotion.quickTween()),
                                    initialContentExit = fadeOut(TagMotion.instantTween()),
                                )
                            } else {
                                val rawDir = when {
                                    initialState.languageSubPage != targetState.languageSubPage ->
                                        if (targetState.languageSubPage) 1 else -1
                                    else -> targetState.tab.ordinal.compareTo(initialState.tab.ordinal).coerceIn(-1, 1)
                                }
                                val dir = if (isRtl) -rawDir else rawDir
                                ContentTransform(
                                    targetContentEnter = fadeIn(TagMotion.standardTween()) +
                                        slideInHorizontally(TagMotion.standardTween()) { width ->
                                            (width * 0.08f * dir).toInt()
                                        } +
                                        scaleIn(initialScale = 0.985f, animationSpec = TagMotion.standardTween()),
                                    initialContentExit = fadeOut(TagMotion.quickTween()) +
                                        slideOutHorizontally(TagMotion.quickTween()) { width ->
                                            (-width * 0.06f * dir).toInt()
                                        },
                                )
                            }
                        },
                        label = "root-screen-transition",
                    ) { dest ->
                        if (dest.languageSubPage) {
                            LanguageScreen(
                                current = state.settings.language,
                                onSelect = viewModel::setLanguage,
                                onBack = { showLanguagePage = false },
                            )
                        } else {
                            when (dest.tab) {
                                Tab.TODAY -> TodayScreen(
                                    state = state,
                                    onToggleRoutine = viewModel::toggleRoutine,
                                    onSelectDate = viewModel::selectDate,
                                    onAddRoutine = { creatingRoutine = true },
                                    onEditRoutine = { editingRoutine = it },
                                    onDeleteRoutine = { routine ->
                                        if (routine.repeatDaily) {
                                            pendingDeleteRecurring = routine
                                        } else {
                                            viewModel.deleteRoutine(routine.id)
                                            offerUndo(undoRoutineLabel) { viewModel.restoreRoutine(routine) }
                                        }
                                    },
                                    // The Today hero pill jumps to the dedicated Focus
                                    // section, which owns duration selection and history.
                                    onStartFocus = { tab = Tab.FOCUS },
                                    onStartFocusForRoutine = { routine ->
                                        when {
                                            !focus.active -> {
                                                viewModel.startFocus(routine, state.settings.focusMinutes)
                                                tab = Tab.FOCUS
                                            }
                                            focus.routineId == routine.id -> {
                                                tab = Tab.FOCUS
                                            }
                                            else -> {
                                                pendingSwitchFocusRoutine = routine
                                            }
                                        }
                                    },
                                    onMoveRoutine = viewModel::moveRoutine,
                                    onOpenTemplates = { showTemplatesSheet = true },
                                    onDismissTemplatesCard = viewModel::dismissTemplatesCard,
                                    onCopyPreviousDay = viewModel::copyYesterdayToSelected,
                                    onCopyRoutineToDays = viewModel::copyRoutineToDays,
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
                                    calendarType = state.settings.calendarType,
                                    calmMode = focusCalmMode,
                                    onToggleCalmMode = {
                                        focusCalmMode = FocusMotionLogic.resolveCalmMode(
                                            requestedCalm = !focusCalmMode,
                                            hasActiveSession = focus.active,
                                        )
                                    },
                                    onStart = viewModel::startFocus,
                                    onPause = viewModel::pauseFocus,
                                    onResume = viewModel::resumeFocus,
                                    onFinish = viewModel::finishFocus,
                                )

                                Tab.INSIGHTS -> InsightsScreen(state = state)

                                Tab.SETTINGS -> SettingsScreen(
                                    state = state,
                                    backupState = backupState,
                                    onThemeMode = viewModel::setThemeMode,
                                    onThemePalette = viewModel::setTheme,
                                    onDynamicColor = viewModel::setDynamicColor,
                                    onAnimationSetting = viewModel::setAnimationSetting,
                                    onCalendarType = viewModel::setCalendarType,
                                    onNotifications = { enabled ->
                                        viewModel.setNotificationsEnabled(enabled)
                                        if (enabled) onRequestNotificationPermission()
                                    },
                                    onDigest = { enabled ->
                                        viewModel.setDigestEnabled(enabled)
                                        if (enabled) onRequestNotificationPermission()
                                    },
                                    onDigestMinutes = viewModel::setDigestMinutes,
                                    onExportUri = viewModel::exportBackupToUri,
                                    onImportUri = viewModel::inspectBackupUri,
                                    onDismissBackupDialog = viewModel::dismissBackupDialog,
                                    onConfirmMergeRestore = { viewModel.confirmRestore(BackupMode.MERGE) },
                                    onRequestReplaceRestore = viewModel::requestReplaceConfirmation,
                                    onBackToBackupPreview = viewModel::backToPreview,
                                    onConfirmReplaceRestore = { viewModel.confirmRestore(BackupMode.REPLACE) },
                                    onReset = viewModel::resetProgress,
                                    onAddTag = { name -> viewModel.addTag(name) },
                                    onUpdateTag = viewModel::updateTag,
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
                }
                val navLevel = LocalAnimationLevel.current
                AnimatedVisibility(
                    visible = !(focusCalmMode && tab == Tab.FOCUS && focus.active),
                    enter = when {
                        navLevel.isOff -> fadeIn(TagMotion.instantTween(0))
                        navLevel == AnimationLevel.REDUCED -> fadeIn(TagMotion.quickTween())
                        else -> fadeIn(TagMotion.standardTween()) + slideInVertically(TagMotion.standardTween()) { it }
                    },
                    exit = when {
                        navLevel.isOff -> fadeOut(TagMotion.instantTween(0))
                        navLevel == AnimationLevel.REDUCED -> fadeOut(TagMotion.quickTween())
                        else -> fadeOut(TagMotion.quickTween()) + slideOutVertically(TagMotion.quickTween()) { it }
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PillNavBar(
                            current = tab,
                            onSelect = {
                                // Leaving Settings always drops its sub-page, so returning to
                                // Settings later starts on the list rather than a stale page.
                                if (it != Tab.SETTINGS) showLanguagePage = false
                                tab = it
                            },
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                // Copy/restore feedback is announced by TalkBack as it appears.
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    }

    if (creatingTask || editingTask != null) {
        TaskEditorSheet(
            existing = editingTask,
            tags = state.tags,
            localizedContext = LocalContext.current,
            calendarType = state.settings.calendarType,
            onDismiss = {
                creatingTask = false
                editingTask = null
            },
            onRequestNotificationPermission = onRequestNotificationPermission,
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
        val localizedContext = LocalContext.current
        RoutineEditorSheet(
            existing = editingRoutine,
            localizedContext = localizedContext,
            calendarType = state.settings.calendarType,
            existingRoutines = state.routineHistory,
            onDismiss = {
                creatingRoutine = false
                editingRoutine = null
            },
            onOpenTemplates = if (editingRoutine == null) {
                { showTemplatesSheet = true }
            } else {
                null
            },
            onSave = { title, group, icon, timeMinutes, daysMask, reminder, colorHex, colorId, repeatDaily ->
                val hadReminder = editingRoutine?.let { it.reminder && it.timeMinutes != null } == true
                viewModel.saveRoutine(
                    existing = editingRoutine,
                    title = title,
                    group = group,
                    icon = icon,
                    timeMinutes = timeMinutes,
                    daysMask = daysMask,
                    reminder = reminder,
                    colorHex = colorHex,
                    colorId = colorId,
                    repeatDaily = repeatDaily,
                )
                if (NotificationPolicy.shouldTriggerOnRoutineSave(hadReminder, reminder, timeMinutes != null)) {
                    onRequestNotificationPermission()
                }
            },
        )
    }

    pendingDeleteRecurring?.let { recurring ->
        val localizedContext = LocalContext.current
        LocalizedAlertDialog(
            localizedContext = localizedContext,
            onDismissRequest = { pendingDeleteRecurring = null },
            title = {
                Text(stringResource(R.string.delete_recurring_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.delete_recurring_message, recurring.title))
                    AppButton(
                        text = stringResource(R.string.delete_recurring_only_this_day),
                        onClick = {
                            viewModel.excludeRoutineForDate(recurring.id, state.selectedDate)
                            pendingDeleteRecurring = null
                        },
                        variant = AppButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppButton(
                        text = stringResource(R.string.delete_recurring_from_this_day),
                        onClick = {
                            viewModel.endRoutineFromDate(recurring.id, state.selectedDate)
                            pendingDeleteRecurring = null
                        },
                        variant = AppButtonVariant.Tonal,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingDeleteRecurring = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    pendingSwitchFocusRoutine?.let { targetRoutine ->
        val localizedContext = LocalContext.current
        val currentLabel = focus.routineTitle?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.focus_history_untitled)
        LocalizedAlertDialog(
            localizedContext = localizedContext,
            onDismissRequest = { pendingSwitchFocusRoutine = null },
            title = {
                Text(stringResource(R.string.focus_switch_title))
            },
            text = {
                Text(
                    stringResource(
                        R.string.focus_switch_body,
                        currentLabel,
                        targetRoutine.title,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.switchFocus(targetRoutine, state.settings.focusMinutes)
                        pendingSwitchFocusRoutine = null
                        tab = Tab.FOCUS
                    },
                ) {
                    Text(stringResource(R.string.focus_switch_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSwitchFocusRoutine = null }) {
                    Text(stringResource(R.string.focus_switch_keep))
                }
            },
        )
    }

    if (showTemplatesSheet) {
        val localizedContext = LocalContext.current
        TemplatePickerSheet(
            localizedContext = localizedContext,
            onDismiss = { showTemplatesSheet = false },
            onSelectTemplate = { template ->
                viewModel.applyTemplate(template, localizedContext.resources)
                viewModel.dismissTemplatesCard()
            },
        )
    }
}

