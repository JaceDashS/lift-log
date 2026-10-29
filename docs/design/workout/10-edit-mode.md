# Edit Mode Design
- [Specification](../../specs/workout/10-edit-mode.md)
- [Architecture](../../architecture/10-overview.md)
- [Scenarios](../../testing/scenarios/workout/10-edit-mode.md)

## Inputs And Outputs
Inputs are Android lifecycle events, touch events, slider changes, service actions and saved preferences. Outputs include timer text, selection visuals, transforms, overlay windows, notifications and saved layout values.

## Public Modules
Kotlin paths below are relative to app/src/main/java/com/example/workouttimer. Android lifecycle and listener overrides implement the corresponding framework callbacks.

| Module | Public interface |
| --- | --- |
| MainActivity.kt | onCreate binds launch, reset and log controls; onResume continues startup after overlay permission |
| WorkoutActivity.kt | onCreate initializes in-app editing; onDestroy releases resources |
| OverlayService.kt | onCreate initializes service; onStartCommand handles ACTION_START_SERVICE, ACTION_END_SESSION, ACTION_START_TIMER, ACTION_RESET_SET and ACTION_ENABLE_EDIT_MODE; onBind returns null; onDestroy releases resources |
| TouchAwareFrameLayout.kt | Constructor accepts Android view attributes; dispatchTouchEvent returns normal dispatch handling and calls onPostDispatchTouchEvent in finally |
| StrokeTextView.kt | Constructor reads outline attributes; setTextColor overloads retain fill color; onDraw draws optional outline then fill |
| SelectionDebugLogger.kt | log accepts Context, source and message, writes Logcat and attempts file append; logPath returns internal path; clear attempts deletion and returns that path |
| constants/AppMagicNumbers.kt | Named constants grouped by UI, timer, overlay, interaction, slider, alpha and notification concerns |
| OverlayPreferences.kt | Context based Preferences DataStore access described below |

Layouts: app/src/main/res/layout/activity_main.xml, activity_workout.xml, overlay_workout.xml, overlay_controls.xml and overlay_controls_bottom.xml. Resource values supply strings, styling and numeric settings.

## Persistence Contracts
OverlayPreferences.state emits OverlayState with panel coordinates and bounded alpha. OverlayState holds panel X, panel Y and alpha. ElementTransform holds nullable coordinates, axis scales and rotation. WorkoutViewportSize holds width and height. MIN_ALPHA, MAX_ALPHA and DEFAULT_ALPHA expose alpha bounds and default.

| Function | Input | Output or effect |
| --- | --- | --- |
| savePosition | X and Y | Saves panel coordinates |
| saveAlpha | Alpha | Saves clamped alpha |
| saveElementPosition | Element ID, X and Y | Saves element coordinates |
| readElementPosition | Element ID | Coordinate pair or null if incomplete |
| saveElementTransform | ID, nullable X and Y, scales and rotation | Saves nonnull coordinates, scales and rotation |
| readElementTransform | Element ID | Transform with legacy scale fallback and defaults of scale 1 and rotation 0 |
| saveWorkoutViewportSize | Width and height | Saves dimensions |
| readWorkoutViewportSize | None | Positive dimensions or null |
| clearAll | None | Removes all preferences |

## Representative Scenario
- Input: Set and total are selected.
- Steps: Tap blank panel space; root post-dispatch handling clears selection.
- Expected result: Selection visuals return to defaults.
