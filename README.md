# ✅ Tag — Daily Routine & Task Planner (v1.3)

A clean and customizable Android routine and task planner with day-centric scheduling, a built-in focus timer, unified Material 3 design system, optional Dynamic Color, and full support for five languages.

📲 Download the latest APK from the [Releases](https://github.com/TahaAbbasi211/tag-to-do-list/releases) page.

## ✨ Features

- 📅 **Day-centric Today timeline**: Each routine/item belongs to its selected day (past, today, or future) with optional daily repeat (`Repeat daily`)
- 🗑️ **Flexible recurring delete**: Remove a repeating item for today only or from this day onwards without losing historical completion logs
- 📑 **Copy items between days**: One tap copies yesterday's items onto the selected day, and any item's menu offers **Copy to another day** with a multi-day picker. Only items are copied — never the tick state — and pressing the action twice never creates duplicates
- ⚡ **Quick add**: While typing a title, up to five suggestions are drawn from your existing items (ranked by title similarity and by how often you use them); one tap fills the title, icon, colour, time and group
- 💬 **Dynamic Today hero line**: A short message on the Today card reacts to the hour and to your progress ("Just one left!", "Shall we plan tomorrow?", or a neutral line when the day is empty)
- ⏱️ **Item-connected Focus timer**: Start a focus session directly from any Today item's menu (runs cleanly in the background with lock-screen & notification support), see **Today's focus** right on the Today hero card, and review **Most focused items** alongside total focus time in Insights (preserving unlinked sessions)
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

The suite currently holds 128 tests, covering daily focus totals, unlinked focus sessions and per-item focus ranking (`FocusSummaryEngine`), copy rules, quick-add ranking, dynamic Today line, plus the existing date, stats, focus timer, ordering, migration and localization guards.

## 🔮 Future Improvements

- [x] Item-linked focus sessions and per-item focus insights (v1.3)
- [ ] Focus goals & Pomodoro break intervals
- [ ] Task categories and due dates
- [ ] Cloud backup and sync
- [ ] Home screen widget

## 🤝 Contributing

Contributions are welcome! Feel free to open an issue or submit a pull request.

## 📄 License

This project is licensed under the [MIT License](LICENSE).

## 📝 Changelog

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
