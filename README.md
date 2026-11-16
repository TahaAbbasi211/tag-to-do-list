# ✅ Tag — Daily Routine & Task Planner (v1.1.1)

A clean and customizable Android routine and task planner with day-centric scheduling, a built-in focus timer, unified Material 3 design system, optional Dynamic Color, and full support for five languages.

📲 Download the latest APK from the [Releases](https://github.com/TahaAbbasi211/tag-to-do-list/releases) page.

## ✨ Features

- 📅 **Day-centric Today timeline**: Each routine/item belongs to its selected day (past, today, or future) with optional daily repeat (`Repeat daily`)
- 🗑️ **Flexible recurring delete**: Remove a repeating item for today only or from this day onwards without losing historical completion logs
- 📝 Add, edit, complete, and delete tasks & routines
- ⏱️ Built-in focus timer to stay focused on your routines
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

## 🔮 Future Improvements

- [ ] Task categories and due dates
- [ ] Cloud backup and sync
- [ ] Home screen widget

## 🤝 Contributing

Contributions are welcome! Feel free to open an issue or submit a pull request.

## 📄 License

This project is licensed under the [MIT License](LICENSE).

## 📝 Changelog

- **1.1.1**:
  - Fixed hairline pink/coral border bleed on Today and Tasks cards by removing settled `SwipeToDelete` background from composition and matching card corner shapes
  - Made Today items day-centric (`dateEpochDay` with optional `repeatDaily` toggle, Room schema v4 + lossless `MIGRATION_3_4`, past/future date creation, per-day vs from-date delete options for recurring items, and neutral empty days in streak calculation)
  - Refined design system tokens, contrast ratios (>= 4.5:1 across palettes), `AppCard`/`AppButton`/`StatTile` unification, and optional Dynamic Color in Settings
- **1.1**: Vertical single-day timeline layout on Today screen
- **1.0.9**: Fix weekday labels locale, digit formatting, list padding, template card visibility, contrast, card shape clipping
