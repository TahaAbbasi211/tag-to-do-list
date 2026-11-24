# ✅ Tag — Daily Routine & Task Planner (v1.9)

A clean and customizable Android routine and task planner with day-centric scheduling, Gregorian & Solar Hijri (Jalali) calendar support, safe local JSON backup & restore, a built-in focus timer, unified Material 3 design system, optional Dynamic Color, an interactive home screen widget, full TalkBack and large-font support, tablet/landscape layouts, and full support for five languages.

📲 Download the latest APK from the [Releases](https://github.com/TahaAbbasi211/tag-to-do-list/releases) page.

## ✨ Features

- ♿ **Accessibility & large screens (v1.9)**: Every interactive control has a 48dp touch target and a meaningful, translated description; decorative icons are hidden from TalkBack. Each Today row is one merged semantics node that reads `title, time, status` in a single pass and exposes **Delete / Edit / Copy to another day / Move up / Move down** as custom accessibility actions, so swipe-to-delete is never the only way to act. Section and screen titles are headings, progress rings, the weekly bar chart and every heatmap cell announce their value (date, percentage, counts) using the selected Gregorian or Jalali calendar and localized digits, and completion is conveyed by a tick plus strikethrough rather than colour alone. Snackbars and backup/copy results are polite live regions, the system “remove animations” setting disables confetti, tick and ring motion, and font scales up to 2.0 switch side-by-side rows to stacked layouts or scrollable strips instead of clipping. On wide screens (> 600dp) Today, Tasks, Focus, Insights and Settings content is capped and centred, and sheets/dialogs no longer stretch edge to edge. All label-building logic lives in a pure, unit-tested `AccessibilityLabels` class

- 🧷 **Home screen widget (`TodayWidgetProvider`, `WidgetActionReceiver`, `WidgetStateBuilder`, RemoteViews)**: A resizable widget showing today's progress as a percentage and a "3 of 5 done" counter, plus up to three upcoming unfinished items (icon, title, time) in the wider 4x2 size with a dedicated `40dp` completion button on each row. Tapping a tick button marks that item done idempotently via `WidgetActionReceiver` (`exported="false"`) and the same `TagRepository.toggleRoutine` log path used by Today and notification actions, dismisses any active reminder notification for that item, optionally triggers a short click haptic (using the existing `VIBRATE` permission when enabled in Settings), and refreshes the widget so the next open item promotes into place, the counter/percentage update, and a completed day shows the all-done state. Stale taps from a previous day (after midnight), deleted/unscheduled items, or double-taps never write duplicate logs and simply re-sync the widget. Tapping a row's body, the rest of the wide widget, or anywhere on the unchanged 2x2 compact widget opens the app. Refreshes on every item/task add, edit, delete, tick, un-tick and backup restore, at midnight (`DATE_CHANGED`), on time-zone or clock changes, after a reboot or app update, and on a 30-minute platform tick. Follows the in-app language and digit style, respects the chosen calendar, and ships light/dark resources with RTL mirroring and localized accessibility descriptions in all 5 languages

- 💾 **Safe Backup & Restore (`BackupCodec`, `BackupCoordinator`, SAF)**: Export a full JSON backup (`formatVersion = 3` with app version, timestamp, routines with day-centric fields, tasks, tags, completion logs, focus sessions, and settings) via Android's Storage Access Framework (`CreateDocument`) with zero storage permissions. Restore via `OpenDocument` with bounded streaming validation, a pre-restore preview (creation date, item/task/focus counts, date range), **Merge** (non-destructive, duplicate-safe) or **Replace** (with an explicit warning), automatic safety backups in internal storage (last 3 kept), atomic Room `withTransaction` execution, live UI/stats refresh, reminder rescheduling/cleanup, and backward compatibility with legacy backups without `formatVersion`

- 🗓️ **Gregorian & Solar Hijri (Jalali) calendar**: Switch between **Gregorian** (default) and **Solar Hijri (Jalali)** anytime in Settings without restarting or modifying stored data — featuring Persian month names (`فروردین… اسفند`) across all 5 app languages, Saturday-start weeks, a custom Jalali date picker in Tasks, month/year headers on the Week Strip, Jalali day numbers in Copy to Another Day, month labels and Saturday-aligned columns in the Insights Heatmap, Saturday–Friday boundaries in the Weekly Report, and Jalali timestamps in Focus history
- 📅 **Day-centric Today timeline**: Each routine/item belongs to its selected day (past, today, or future) with optional daily repeat (`Repeat daily`)
- 🗑️ **Flexible recurring delete**: Remove a repeating item for today only or from this day onwards without losing historical completion logs
- 📑 **Copy items between days**: One tap copies yesterday's items onto the selected day, and any item's menu offers **Copy to another day** with a multi-day picker. Only items are copied — never the tick state — and pressing the action twice never creates duplicates
- ⚡ **Quick add**: While typing a title, up to five suggestions are drawn from your existing items (ranked by title similarity and by how often you use them); one tap fills the title, icon, colour, time and group
- 💬 **Dynamic Today hero line**: A short message on the Today card reacts to the hour and to your progress ("Just one left!", "Shall we plan tomorrow?", or a neutral line when the day is empty)
- ⏱️ **Item-connected Focus timer**: Start a focus session directly from any Today item's menu (runs cleanly in the background with lock-screen & notification support), see **Today's focus** right on the Today hero card, and review **Most focused items** alongside total focus time in Insights (preserving unlinked sessions)
- 📊 **Weekly Report & Interactive 12-week Heatmap in Insights**: View current and previous week completion rates (with neutral empty days and honest incomplete-week comparison), perfect days, streak, strongest and weakest items, plus a tap-to-inspect 12-week heatmap with theme-aware intensity and RTL/LTR week alignment
- 🧩 **Refined UI across Tasks, Focus, Insights & Settings**: Clear visual hierarchy (`ScreenHeader`, `SectionHeader`), unified `AppCard`/`AppButton`/`AppChip`/`StatTile` components, meaningful `EmptyState`s, and resilient Light/Dark, RTL, and large system font layouts
- 📝 Add, edit, complete, and delete tasks & routines
- 🎨 Unified design system with multiple high-contrast themes and optional Material You Dynamic Color
- 🌍 Multi-language support (with RTL & localized digits):
  - فارسی (Persian)
  - English
  - Deutsch (German)
  - Türkçe (Turkish)
  - Français (French)
- 💾 Offline storage with Room (schema v4) and DataStore Preferences
- 🔔 Reminders and notifications (including a daily digest)

## 🛠️ Built With

- Kotlin
- Jetpack Compose with Material 3 (Compose BOM 2024.12.01)
- Room 2.6.1 (KSP, schemas exported) and DataStore Preferences 1.1.1
- Android Studio

## 📋 Requirements

- Android Studio Ladybug (2024.2.1) or newer
- Android SDK API 35
- JDK 17
- A device or emulator running Android 8.0 (API 26) or newer

## 🚀 Getting Started

1. Clone the repository:

   ```bash
   git clone https://github.com/TahaAbbasi211/tag-to-do-list.git
   ```

2. Open the project in **Android Studio**.
3. Wait for Gradle to sync.
4. Run the app on an emulator or a physical device.

## 🧪 Tests

Run the unit tests with:

```bash
./gradlew :app:testDebugUnitTest
```

The unit test suite covers the widget action decider (`WidgetActionDeciderTest`: valid mark-done actions, already-done/double-tap idempotency, missing/unscheduled routines, post-midnight stale dates, queue progression, and unique PendingIntent request codes), the widget state builder (`WidgetStateBuilderTest`: empty day, fully done day, partly done day, repeating and one-off items, next-item ordering by clock time, and the midnight boundary), widget manifest/layout/ProGuard guards (`WidgetConfigTest`), accessibility sentence builders for timeline rows, heatmap cells (Gregorian and Jalali, Persian digits) and chart summaries (`AccessibilityLabelsTest`), the reduced-motion rule (`MotionPreferencesTest`), backup validation, streaming bounds, legacy compatibility, duplicate-free merge, replace, round-trip fidelity, safety-backup retention, and mid-restore rollback (`BackupManagerTest`), Solar Hijri (Jalali) conversions, leap years, year boundaries, Saturday-start weeks and month labels (`JalaliCalendarTest`, `DatesTest`), weekly reporting (`WeeklyReportEngine`), 12-week calendar heatmap building (`HeatmapCalendar`), daily focus totals, unlinked focus sessions and per-item focus ranking (`FocusSummaryEngine`), copy rules, quick-add ranking, dynamic Today line, plus the existing stats, focus timer, ordering, migration and localization guards.

## 🔮 Future Improvements

- [x] Item-linked focus sessions and per-item focus insights (v1.3)
- [x] Solar Hijri (Jalali) calendar display and date selection (v1.5)
- [x] Safe local JSON backup & restore with preview, merge/replace, and automatic safety backups (v1.6)
- [ ] Focus goals & Pomodoro break intervals
- [ ] Task categories & recurring tasks
- [ ] Cloud backup and sync
- [x] Home screen widget with today's progress and upcoming items (v1.7)
- [x] One-tap item completion directly from the wide home screen widget (v1.8)
- [x] TalkBack semantics, alternative swipe actions, large-font layouts and centred wide-screen content (v1.9)

## 🤝 Contributing

Contributions are welcome! Feel free to open an issue or submit a pull request.

## 📄 License

This project is licensed under the [MIT License](LICENSE).

## 📝 Changelog

- **2.0 beta 1 (in progress)**:
  - **Motion Infrastructure & Living Theme (Phase 1)**: Added unified `TagMotion` tokens (durations, cubic-bezier easings, springs), `AnimationPolicy` (`Auto` / `Full` / `Reduced` / `Off` in Settings and backward-compatible JSON backup/restore, respecting system "Remove animations" and low-RAM devices), lifecycle-aware ambient gating (`LocalAmbientAnimationsActive`), `DayPhase` time-of-day hero & aurora tints (with Dynamic Color derivation), `HapticKind` feedback, zero-recomposition modifiers (`pressScale`, `staggeredEnter`, `breathing`, `floatY`, `glowPulse`), and pure-Canvas components (`AuroraBackground`, `GlassCard`, `CountUpText`, `AnimatedRing`, deterministic `SparkleBurst`/`ConfettiBurst`, `StreakFlame`, `DigitRoll`, `CheckDraw`) with 5-language strings (`en`, `fa`, `de`, `fr`, `tr`) and unit tests
  - **Living Today Screen (Phase 2)**: Upgraded the Today hero onto `AuroraBackground` + `GlassCard` with scroll parallax and collapsible compact bar, initial ring fill & count-up, word-by-word greeting, crossfading nudge, `StreakFlame` bump + milestone banners (`StreakMilestones`), once-per-day 100% confetti/sparkle celebration, RTL-aware spring-sliding `WeekStrip` with staggered dots and Today glow, progressive `TimelineRoutineItem` line draw + completion ripple + LTR/RTL animated strikethrough + Up-Next glow, elastic `SwipeToDelete` (keeping zero settled background bleed), animated Canvas sunrise `TodayEmptyState`, and scroll-collapsing FAB
  - **Navigation & Transitions (Phase 3)**: Added an RTL-aware spring-sliding pill and outline-to-filled icon morph to `PillNavBar`/`AppBottomNav`, shared-axis X + fade-through `AnimatedContent` tab transitions, spring scale+fade entrances for dialogs and bottom sheets, and smooth theme/palette crossfades (`versionCode = 20`, `versionName = "2.0-beta1"`)

- **1.9**:
  - **Screen-reader semantics (`AccessibilityLabels`, `TimelineRoutineItem`, `AccessibilityLabelsTest`)**: Pure, unit-tested builders produce one-sentence row labels (`title, time, status`, including “no time set”), checkbox state descriptions, heatmap cell labels (calendar date + completion percentage + `completed of scheduled`, with Persian digits and Jalali month names) and seven-day chart summaries. Today rows merge their descendants into a single TalkBack node, `AnimatedCheck` exposes `stateDescription`, section and screen titles use `heading()`, and completion stays visible through a tick and strikethrough rather than colour alone
  - **Alternative actions instead of swipe-only (`CustomAccessibilityAction`)**: Every Today row and every Tasks row now offers Delete, Edit, Copy to another day and Move up/down (where available) through TalkBack’s actions menu. Deleting still runs through the existing confirmation dialog for recurring items and the existing Undo snackbar for one-off items, so no data path changed
  - **Charts, feedback and touch targets**: Progress rings (Today, Insights, Focus) and the weekly bar chart announce their value; each heatmap cell describes its date, percentage and counts and the heatmap legend explains the intensity scale in text; snackbars and copy/backup results are polite live regions; icon buttons, checkboxes, chips, hero pills, emoji/colour/day pickers and Jalali calendar cells now meet the 48dp minimum touch target
  - **Large fonts, reduced motion and RTL**: At font scales up to 2.0 the Today hero stacks its ring under the greeting, stat tiles stack vertically, the week strip scrolls horizontally, form chip rows wrap, ring sizes grow with the font, and sheets scroll. When the system “remove animations” setting is on, confetti is skipped and tick/ring/pulse animations snap to their final state. RTL, light/dark themes and the widget are unchanged
  - **Large screens (`LargeScreenFrame`)**: Today, Tasks, Focus, Insights and Settings cap their content at a fixed width and centre it on screens wider than roughly 600dp, the bottom navigation is capped and centred, and bottom sheets and custom dialogs use a bounded width. FABs and lists keep their existing scroll padding so landscape phones never clip content
  - **Version & scope**: `versionCode = 19`, `versionName = "1.9"`, ten new strings in all five languages, no new permissions, no new dependencies (`material3.windowsizeclass` is not used), and no changes to stats, calendar, backup, focus-timer or widget logic

- **1.8**:
  - **One-Tap Completion on Wide Widget (`WidgetActionReceiver`, `WidgetRenderer`, `widget_today_wide`)**: Added a `40dp` circular tick button (`ImageView` with `ic_widget_mark_done` and light/dark `widget_check_btn_bg` ripple) to the end of each upcoming row in the 4x2 wide widget (automatically placed on the left in RTL Persian). Tapping the tick button completes that item; tapping the row body, the rest of the wide widget, or anywhere on the unchanged 2x2 compact widget still opens the app
  - **Idempotent Pure Decision Logic (`WidgetActionDecider`, `WidgetState.epochDay`)**: Extracted pure-JVM validation (`WidgetActionDecider.decide`) enforcing mark-done-only (no untoggle) semantics. Stale actions from before midnight (`epochDay != today`), deleted or unscheduled items, or already-completed items never write duplicate logs and only trigger a widget refresh; `WidgetActionReceiver` runs behind `goAsync` on `Dispatchers.IO` under a `Mutex`, reuses `TagRepository.toggleRoutine`, dismisses any active reminder notification (`NotificationManagerCompat.cancel(routineId.hashCode())`), triggers a light touch haptic if enabled in Settings (reusing the existing `VIBRATE` permission), and calls `WidgetUpdater.updateAll` so completed items drop out, the next open item rotates in, percentages/counters update, and finishing all items switches to the all-done state
  - **Manifest, Localization & Tests**: Added only `<receiver android:name=".widget.WidgetActionReceiver" android:exported="false" />` (zero new `<uses-permission>` entries, zero new dependencies), added 5-language `contentDescription` strings (`en`, `fa`, `de`, `fr`, `tr`), bumped `versionCode = 18` / `versionName = "1.8"`, and added `WidgetActionDeciderTest` plus updated `WidgetConfigTest` guards
- **1.7**:
  - **Home Screen Widget (`TodayWidgetProvider`, `widget_today_info`, RemoteViews)**: Added a resizable home screen widget built on `AppWidgetProvider` and `RemoteViews` (no Glance, no new dependency, no schema change). The compact 2x2 layout shows today's completion percentage with a progress bar and a "3 of 5 done" counter; the wider 4x2 layout adds up to three upcoming unfinished items with icon, title and time. A day with nothing scheduled shows a neutral message rather than a "0%" failure, and a fully completed day gets its own message. Tapping the widget opens the app; ticking items from the widget is intentionally not implemented
  - **Pure, Tested State Logic (`WidgetStateBuilder`)**: All widget arithmetic lives in a pure Kotlin class that takes routines, routine logs, a date and the current time and returns a simple model (mode, percent, counter, next items). It calls the existing `StatsEngine.scheduledOn` and reads the same `routine_logs` rows as `AppViewModel`, and truncates the percentage exactly like the Today screen, so the widget and the app always agree. Covered by 20 unit tests including empty days, complete days, partial days, repeating and one-off items, ordering by clock time, and the midnight boundary
  - **Automatic Refresh (`WidgetUpdater`, `WidgetRefresh`, `WidgetUpdateReceiver`)**: The widget redraws after every routine/task add, edit, delete, tick, un-tick, copy, reorder and backup restore, at midnight (`ACTION_DATE_CHANGED`), on clock and time-zone changes, after a reboot or app update, and on the platform's 30-minute `updatePeriodMillis` tick. Database reads run off the main thread behind `goAsync` on `Dispatchers.IO`, and every failure path falls back to the neutral state, so a fresh install with an empty database cannot crash it. No exact alarms and no new permissions
  - **Localized, Theme-Aware Appearance (`WidgetLocale`, `values-night`)**: The widget resolves strings through the app's own language setting (not just the system language), formats digits and percentages with `Dates.formatDigits` / `Dates.formatPercent`, respects the selected Gregorian/Jalali calendar for times, and mirrors correctly for RTL Persian. Light and dark palettes use the app's violet/navy identity with rounded corners, every text pair clears WCAG AA 4.5:1, and the widget picker shows a preview layout with a short description
  - **Manifest & Tests**: Added only the widget receivers (no new `<uses-permission>`; the permission list is byte-identical to 1.6), 5-language strings (`en`, `fa`, `de`, `fr`, `tr`), and 20 unit tests in `WidgetStateBuilderTest`
- **1.6**:
  - **Versioned Backup Format (`BackupCodec` v3 + Legacy Compatibility)**: Added `formatVersion`, `appVersion`, `appVersionCode`, and `exportedAt` metadata alongside all 6 Room tables (`routines`, `tasks`, `tags`, `routineLogs`, `taskLogs`, `focusSessions`) and user `settings`, while staying backward-compatible with earlier backups lacking `formatVersion`, `focusSessions`, `settings`, or v4 routine fields
  - **Storage Access Framework Export & Restore (`SettingsScreen`, `BackupFilePicker`)**: Replaced fixed internal-path export with `ActivityResultContracts.CreateDocument` (dated filename suggestion `tag-backup-YYYY-MM-DD.json`) and upgraded `OpenDocument` restore with zero extra manifest permissions and clear success/error feedback
  - **Streaming Validation, Preview & Merge/Replace (`JsonStream`, `BackupDialogs`)**: Added bounded streaming JSON parsing (protecting against oversized/corrupt files), strict schema/type/date/duplicate-ID validation before any write, a pre-restore summary dialog (item/task/focus/tag counts, date range, creation date), duplicate-safe **Merge** with ID remapping, and **Replace** guarded by an explicit warning confirmation
  - **Atomic Transactions, Safety Backups & Reminders (`BackupCoordinator`, `SafetyBackupStore`, `ReminderScheduler`)**: Automatically saves a timestamped safety backup in `filesDir/safety-backups` (keeping the latest 3) before every Merge or Replace, executes DB writes in Room `withTransaction` with rollback on mid-restore failure, updates UI/stats immediately without restart, and reschedules restored reminders while cancelling alarms for removed routines
  - **Tests & Localization**: Added 5-language strings (`en`, `fa`, `de`, `fr`, `tr`) with RTL & Light/Dark dialog support, and added 12 unit tests in `BackupManagerTest`
- **1.5**:
  - **Solar Hijri (Jalali) Calendar Option (`CalendarType`, `JalaliCalendar`, `Dates`)**: Added a **Calendar type** setting in Settings (**Gregorian** by default, **Solar Hijri (Jalali)** optional) persisted in existing DataStore Preferences (`tag_settings`) with zero database schema changes and zero new dependencies. Stored Room dates and backups remain Gregorian ISO / `epochDay`
  - **Shared Jalali Layer (`JalaliCalendar` + `Dates`)**: Implemented `epochDay` ↔ Jalali conversions, 31/30/29-or-30 month lengths, leap-year calculation, Saturday-start week ordering, Persian month names (`فروردین… اسفند`) across all 5 app languages, and `Dates.formatDigits` formatting using Android ICU (`Calendar` with `@calendar=persian` pinned to `TimeZone.GMT_ZONE` / UTC noon to avoid day shifts)
  - **Live Calendar Updates Across All Screens**: Updated `WeekStrip` (with month/year header), `TodayScreen` hero date label, `CopyToDayDialog`, `RoutineEditorSheet` (Saturday-first weekday selector preserving `daysMask` bits), `TaskEditorSheet` (new `JalaliDatePickerDialog` with month/year navigation and year selector), `TasksScreen` due labels, `FocusScreen` history timestamps, and Insights `HeatmapGrid` (month labels + Saturday alignment) & `WeeklyReportCard` (Saturday–Friday boundaries + formatted week span) so changing calendar type updates every screen immediately without restart
  - **Tests & Localization**: Added 5-language strings (`en`, `fa`, `de`, `fr`, `tr`), verified RTL/LTR, Light/Dark, and large system font layouts, and added unit tests covering ICU-verified reference dates, 60+ year exhaustive `epochDay` round-trips, month lengths, leap/common Esfand boundaries, Saturday week starts, heatmap month labels, and weekly report boundaries
- **1.4**:
  - **Weekly Report (`WeeklyReportEngine`)**: Pure-Kotlin weekly summary built from existing `StatsEngine` outputs (`Insights` and `routineRates`) with neutral handling for days with zero scheduled items, current vs previous week completion percentages, perfect days count, streak, strongest & weakest items, honest incomplete-week handling (avoiding misleading partial-vs-full comparisons), and full unit-test coverage (`WeeklyReportEngineTest`) including empty weeks, incomplete weeks, comparisons, month/leap-year boundaries, year transitions, and multi-year history
  - **Interactive 12-Week Heatmap (`HeatmapCalendar`, `HeatmapGrid`)**: Calendar-aligned 12-week contribution heatmap with distinct neutral vs 0%-done vs theme-tinted completion intensity levels, proper RTL/LTR week and weekday ordering (Saturday-start for Persian, Monday-start otherwise), tap-to-inspect day summary (scheduled items, checked items, completion percentage), and non-clipping layout for small screens & large system fonts
  - **Insights Layout & Empty State (`InsightsScreen`)**: Added the Weekly Report card (`AppCard` + `StatTile`) and interactive heatmap while keeping all existing Insights sections intact, added a true empty-state check so brand-new users see `EmptyState`, formatted all digits and percentages with `Dates.formatDigits` / `Dates.formatPercent`, and added 5-language translations (`en`, `fa`, `de`, `fr`, `tr`) without changing schema v4 or dependencies
- **1.3**:
  - **Layout redesign (Tasks, Focus, Insights, Settings)**: Introduced clear page and section hierarchy (`ScreenHeader`, `SectionHeader`), consistent `AppCard` surfaces and `MaterialTheme.spacing` rhythm, readable `AppChip` filter bars in Tasks, a prominent ring timer & `AppButton` controls in Focus, theme-consistent `StatTile` grids and charts in Insights, grouped titled sections in Settings, contextual `EmptyState`s, readable done-item contrast, and verified Light/Dark, RTL, and large system font scaling
  - **Item-linked Focus**: Added **Start focus** to every Today item's menu to launch the background timer linked to that item's `routineId` (reusing the existing `focus_sessions.routineId` column and `FocusTimerService` without changing schema v4)
  - **Today & Insights focus reporting**: Added **Today's focus** (`Dates.formatDigits` + localized units) on the Today hero card, and added total focus time plus **Most focused items** ranking in Insights while keeping previously recorded unlinked sessions counted
  - Added pure-Kotlin `FocusSummaryEngine` and unit tests (`FocusSummaryEngineTest`) for daily focus totals, unlinked/deleted-routine sessions, and per-item aggregation, plus 5-language translations (`en`, `fa`, `de`, `fr`, `tr`)
- **1.2**:
  - Added **Copy yesterday to today**: one tap recreates the previous day's one-off and repeating items on the selected day. Repeating items that already show on that day are skipped, and copies never carry the completion state
  - Added **Copy to another day** to every item's menu, with a picker for one or more days. Copying creates items only, and a second press produces no duplicate
  - Added **quick-add suggestions** to the routine editor: up to five titles from your existing items, ranked by title similarity and usage count, filling title, icon, colour, time and group in one tap. Reads existing data only
  - Added a **dynamic line on the Today hero card** driven by the time of day and completion ratio, with a neutral message when nothing is scheduled
  - Copy and suggestion logic lives in pure Kotlin (`CopyEngine`, `SuggestionEngine`, `TodayNudge`) and is covered by unit tests; all new text is translated into English, Persian, German, French and Turkish with localized digits and RTL support
- **1.1.1**:
  - Fixed hairline pink/coral border bleed on Today and Tasks cards by removing settled `SwipeToDelete` background from composition and matching card corner shapes
  - Made Today items day-centric (`dateEpochDay` with optional `repeatDaily` toggle, Room schema v4 + lossless `MIGRATION_3_4`, past/future date creation, per-day vs from-date delete options for recurring items, and neutral empty days in streak calculation)
  - Refined design system tokens, contrast ratios (>= 4.5:1 across palettes), `AppCard`/`AppButton`/`StatTile` unification, and optional Dynamic Color in Settings
- **1.1**: Vertical single-day timeline layout on Today screen
- **1.0.9**: Fix weekday labels locale, digit formatting, list padding, template card visibility, contrast, card shape clipping
