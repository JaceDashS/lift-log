# Glossary
## Dev Mode
Activity based editing in WorkoutActivity.
## Overlay Mode
Workout UI supplied by OverlayService above other applications.
## Single Selection
One active element with a selection box and resize handles.
## Multi Selection
Selection formed by long press and drag, with orange highlighting.
## Touch Through
Touches pass through the main overlay to the underlying application.

## Lift Log
The application product name, supplied by the app_name string resource.

## Session
- Definition: One workout from START to END, made of sets.
- Code: `SessionActivity`
- Avoid: "workout" for a single set

## REST and PUSH
- Definition: The two session states. REST runs the rest timer; PUSH means lifting, with the rest timer paused.
- Code: `session_state_rest`, `session_state_push`
- Avoid: "start" for PUSH

## Exercise Name
- Definition: Free text the user types for an exercise, such as bcpr for bench press.
- Code: `input_session_exercise`, `PreviewData.ExerciseSummary.name`

## Chart Spacing
- Definition: How the exercise chart lays out its x axis: one slot per session (default) or one slot per day.
- Code: `TrendChartView.Spacing`

## Test Mode
- Definition: A mode that works with made-up records kept apart from real records.
- Code: `switch_settings_test_mode`

## Preview Data
- Definition: Fixed sample content shown by the UI preview screens until recording is implemented.
- Code: `PreviewData`
