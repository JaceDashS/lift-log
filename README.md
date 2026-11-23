# Lift Log

[Product overview and workout example](https://jace-s.com/en/apps/lift-log/)

Android workout logging app. Its core feature is a semi-transparent workout overlay: START floats the rest timer over any app, so the screen behind stays usable while the timer is visible. Home offers START, RECORDS and SETTINGS. Choose an exercise and enter its weight and reps to log a set. The app stores workouts locally. Records displays a comparison chart and uses example data in debug builds when no workouts have been saved. See [App Screens](docs/specs/workout/30-app-screens.md).

Download the signed Android APK from [GitHub Releases](https://github.com/JaceDashS/lift-log/releases/latest). On Android and iOS, open Settings and choose Feedback to write a public guest comment in the app. Use the password you enter there to edit or delete the comment on [jace-s.com](https://jace-s.com/comments). About opens the [developer page](https://jace-s.com/about).

The overlay layout has two editing surfaces that must behave the same way:

- `Dev Mode`: activity-based editing screen
- `Overlay Mode`: system overlay editing screen

This README is the project-level reference for app purpose, edit-mode behavior, overlay interaction, debug logging, and basic development workflow.

## Project

- App id: `com.example.workouttimer`
- Package: `com.example.workouttimer`
- Language: Kotlin
- Min SDK: `26`
- Target SDK: `35`
- Compile SDK: `35`
- Build: Gradle Android application

## iPhone prototype

Open `ios/LiftLog.xcodeproj` in Xcode and run the `LiftLog` scheme on an iPhone simulator. The SwiftUI target supports iOS 16 and later. Add an exercise in Settings, open the workout timer and tap PUSH. Tap REST to enter reps and save the set. The saved set appears in Records. The unfinished workout and recorded sets use local app storage.

Records shows average weight, reps and rest as percentage changes on a horizontally scrollable chart. Tap a point to see its actual averages and switch between calendar days and workout sessions above the chart. The Debug build includes Bench Press and Squat samples under Sample, which opens by default when Saved is empty. Live Activities and record editing are later work.

## Modes

### Dev Mode

- Runs inside `WorkoutActivity`
- Used to test and edit layout behavior inside the app
- Intended to follow exactly the same edit rules as overlay edit mode

### Overlay Mode

- Runs inside `OverlayService`
- Draws the workout UI as a system overlay
- Uses the same edit rules as dev mode while edit mode is active
- In normal overlay state, the full-screen overlay is touch-through so the app behind it remains interactive
- Normal overlay control is handled through touchable overlay buttons kept at their original positions and notification actions

## Overlay Interaction Model

Overlay mode has two distinct states.

### Normal Overlay State

- The full-screen overlay stays visible.
- Touches on the main overlay pass through to the app behind it.
- Touchable overlay buttons remain available at the same visual positions for overlay control.
- Notification actions are also available for overlay control.
- The overlay shows the rest timer in the center, `Set`, total workout `Time`, `PUSH`/`REST`, `End` and individual exercise and weight controls.
- `PUSH` freezes the preceding rest time and displays `GO` in the center. `REST` opens the rep entry dialog. Confirming reps saves the set and restarts the timer. Rest is saved only for consecutive sets of the same exercise. Records > Edit records opens the editable set table.
- Layout editing happens before START through SETTINGS > LAYOUT, so `Reset` and `Edit` are not shown in the overlay.

### Overlay Edit State

- Not entered during a workout; the same rules apply in Dev Mode, opened from SETTINGS > LAYOUT before START.
- The full-screen overlay becomes touchable.
- Selection, deselection, move, resize, rotate, and edit-panel interaction are enabled.
- Leaving edit mode returns the overlay to touch-through behavior.

## Editable Elements

The current editable/selectable elements are:

- `timer`
- `set`
- `total`
- `start` button
- `set reset` button
- `edit` button
- `end` button

Text elements and buttons can be selected together.

## Edit Mode Rules

### Core Principles

- Short press and long press have different roles.
- Single selection and multi-selection have different visual states.
- Orange highlight indicates multi-selection state.
- Single selection exists to define the active target for resize and related manipulation.

### Short Press

- A short press selects only the tapped element.
- Short press is always a single-selection action.
- Single selection shows the selection box and resize handles.
- Single selection does not use the orange multi-select highlight.
- Tapping the already-selected element keeps the selection.

### Long Press

- Long press starts multi-selection.
- The pressed element becomes the first item in the multi-select set.
- When long press starts, the selected item immediately enters highlighted multi-select visuals.
- Dragging across other elements while maintaining long press adds them to the selection set.
- Buttons and text items can be selected together through this flow.

### Multi-Selection

- Two or more selected elements are treated as multi-select.
- In multi-select, all selected elements are highlighted in orange.
- The selection box and resize handles are still shown relative to the primary selected element.

### Deselection

- If a click is recognized and it did not land on a selectable element, selection is cleared.
- This blank-space definition includes the edit panel.
- Single selection and multi-selection both clear through the same rule.

### Tapping Selected Elements

- Tapping an already-selected element does not clear selection.
- In single-select, tapping the selected element keeps the current state.
- In multi-select, tapping one of the selected elements keeps the multi-select state.

### Tapping Another Non-Selected Element

- Outside multi-select, tapping another element changes the active single selection to that element.
- In multi-select, tapping another non-selected element ends the previous multi-select and switches to a new single selection on the tapped element.

### Visual Rules

- Single selection: selection box visible, resize handles visible, no orange highlight
- Multi-selection: all selected elements highlighted orange
- Non-selected elements keep default color and default alpha
- When selection is cleared, all elements return to default appearance

### Long Press End State

- The selection set remains after the finger is released.
- Internal long-press tracking ends when the gesture ends.
- If the final selection count is 2 or more, multi-select visuals remain.
- If the final selection count is 1, the state becomes single selection.

### Shared-Mode Rule

- Dev mode and overlay edit mode use the same edit behavior.
- Snap, move, scale, rotate, selection, and deselection rules remain aligned between both modes.
- Edit-panel values and selection rules do not diverge by mode.
- The only intentional difference is that overlay mode is touch-through outside edit mode.

## Current Overlay Fix

The earlier regression happened because the full-screen overlay window was made non-touchable outside edit mode while its control buttons still lived inside that same window. That made the visible buttons non-interactive.

Current structure:

- The full-screen overlay window becomes touch-through outside edit mode.
- Separate touchable overlay button windows stay aligned to the original button positions in that state.
- Entering edit mode makes the full-screen overlay interactive again.

## Current Selection Fix

The recent blank-space deselection issue was caused by relying on `ACTION_UP` in a path where some blank-space taps only produced a usable `ACTION_DOWN`.

Current fix:

- root gesture tracking uses `TouchAwareFrameLayout.onPostDispatchTouchEvent`
- blank-space deselection is executed on blank-space `ACTION_DOWN`
- debug logs are kept for both `ACTION_DOWN` and `ACTION_UP`

Expected successful blank-space deselect sequence:

1. `postDispatch ACTION_DOWN`
2. `handleRootActionDown clearing selection`
3. `clearSelection`

## Debug Logging

Selection debug logs are written to:

- `/data/user/0/com.example.workouttimer/files/selection-debug.log`

Equivalent path on some devices:

- `/data/data/com.example.workouttimer/files/selection-debug.log`

The same messages are also emitted to Logcat with tag:

- `SelectionDebug`

## Clear Log

`Clear Log` is intentionally placed on the main screen before entering developer mode.

Button order:

- `Start Workout`
- `Dev Mode`
- `Reset All Settings`
- `Clear Log`

Pressing `Clear Log` deletes `selection-debug.log`.

## Useful ADB Commands

Delete the log:

```bash
adb shell run-as com.example.workouttimer rm files/selection-debug.log
```

Read the log:

```bash
adb shell run-as com.example.workouttimer cat files/selection-debug.log
```

Pull the log to the host machine:

```bash
adb exec-out run-as com.example.workouttimer cat files/selection-debug.log > selection-debug.log
```

List files in the app internal files directory:

```bash
adb shell run-as com.example.workouttimer ls files
```

## Build And Test

Run tests:

```bash
./gradlew test
```

On Windows:

```bash
gradlew.bat test
```

If local sandbox-specific directories are needed:

```bash
set GRADLE_USER_HOME=%CD%\.gradle-local
set ANDROID_USER_HOME=%CD%\.android-local
gradlew.bat test
```

## Key Source Files

- [HomeActivity.kt](app/src/main/java/com/example/workouttimer/ui/HomeActivity.kt)
- [WorkoutStore.kt](app/src/main/java/com/example/workouttimer/data/WorkoutStore.kt)
- [MainActivity.kt](app/src/main/java/com/example/workouttimer/MainActivity.kt)
- [WorkoutActivity.kt](app/src/main/java/com/example/workouttimer/WorkoutActivity.kt)
- [OverlayService.kt](app/src/main/java/com/example/workouttimer/OverlayService.kt)
- [SelectionDebugLogger.kt](app/src/main/java/com/example/workouttimer/SelectionDebugLogger.kt)
- [activity_main.xml](app/src/main/res/layout/activity_main.xml)
- [activity_workout.xml](app/src/main/res/layout/activity_workout.xml)

## Supplemental Docs

- [edit-mode-rules.md](docs/specs/workout/10-edit-mode.md)
- [selection-debugging.md](docs/guides/20-selection-debugging.md)


- [Documentation index](docs/00-index.md)
