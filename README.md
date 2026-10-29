# WorkOutTimer

Android workout timer app with two editing surfaces that must behave the same way:

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

## Main Screen

The main screen currently exposes these actions:

- `Start Workout`: starts overlay mode
- `Dev Mode`: opens the in-app editing screen
- `Reset All Settings`: clears saved layout and related preferences
- `Clear Log`: deletes the selection debug log file

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
- Available controls are `Start`, `Reset`, `Edit`, and `End`.

### Overlay Edit State

- Entered through the overlay `Edit` button or the notification `Edit` action.
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
