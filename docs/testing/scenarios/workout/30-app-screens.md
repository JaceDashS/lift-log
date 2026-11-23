# App Screen Scenarios
Screen checks using persistent records. Use an emulator and synthetic data for recording verification.

## Home Launch
- Input: Fresh install.
- Steps: Open Lift Log from the launcher.
- Expected result: Home shows LIFT LOG, the latest workout summary, START, RECORDS and SETTINGS. An active workout changes START to RESUME.

## Start Floats the Overlay
- Input: Overlay permission granted.
- Steps: Tap START, then tap a launcher icon covered by the central timer glyphs.
- Expected result: Both timers stay at zero after opening. The launcher icon still responds. The compact exercise group and separate action buttons are interactive. Without overlay permission, the permission screen opens and START continues after returning with permission.

## Overlay Button Opacity

- Steps: Adjust Overlay button opacity in Settings and start or resume the overlay.
- Expected result: The exercise and load controls, target buttons, the phase button and End use the selected opacity. The value persists after reopening the app.

## Add an Exercise and Log a Set
- Input: An exercise with no previous target.
- Steps: Add Bench Press in Settings. Start the overlay, choose Bench Press, enter 4 in Reps, and enter 135 lb in Weight. Tap Start set, adjust the inputs if needed, then tap Start rest.
- Expected result: Start set freezes the displayed rest time. Start rest stores one set with the normalized weight and four repetitions. The total set count shows one. Start rest restarts the timer while the recorded set retains its original rest time.

## Resume and Switch Exercise
- Input: A running or ended workout with a saved Bench Press repetition count of four.
- Steps: Restart the app, open the overlay and choose a different exercise, then choose Bench Press again.
- Expected result: Bench Press retains four repetitions. Choose 선택 안 함 to clear the selection. Save a blank Reps field before logging to omit repetitions.

## End the Workout
- Steps: Start a set and start rest to save it. Start a second set and tap End.
- Expected result: The overlay closes, Home shows one completed set, and history contains the logged set.

## Set Comparison
- Input: Stored bcpr workouts with matching second sets: current 9 reps and 112 seconds rest, previous 10 reps and 90 seconds rest.
- Steps: Tap bcpr and select the latest workout in the dropdown or graph.
- Expected result: Set 2 shows both loads, red down 1 for reps and red up 0:22 for rest; prior rest 1:30 is visible. Unmatched sets show a dash and no delta.

## Chart Spacing
- Input: bcpr detail with workouts seven days apart.
- Steps: Tap Per day, then Per session.
- Expected result: Per session spaces dots evenly. Per day spaces them by date with day ticks; the line spans the six empty days. Empty history shows an empty state and one workout shows one dot. Workout selection survives rotation.

## Three-Metric Trend

- Input: Bench Press workouts containing 60 kg, 10 reps and 60 seconds of rest, then 75 kg, 8 reps and 90 seconds. Add more workouts to exceed the chart width. Include a workout with no rep count.
- Steps: Open Bench Press in Records, swipe horizontally to older workouts, tap the second workout and toggle Reps.
- Expected result: The chart initially shows the latest workout. The three percentage lines share the date axis. The second workout shows +25% weight, −20% reps and +50% rest, with 75 kg, 8 reps and 1:30 rest below the chart. The legend stays fixed during scrolling. Reps can be hidden and shown without changing the selected workout. Its line has a gap at the workout with missing reps.

## Edit a Recorded Set

- Steps: Open Records > Edit records. Tap a weight or reps cell, enter a new value and save. Swipe sideways to edit a rest cell. Return to Records and open the exercise trend.
- Expected result: The table and trend reflect the saved values. Blank reps are allowed. Edits stay in the active dataset. Editing a date moves every set in that workout to the chosen date.

## Test Mode Form
- Input: Settings in a debug build. A release build opens real records and has no test controls.
- Steps: Turn on Test mode, enter a date, exercise, kg, reps and rest, then ADD RECORD. Generate samples, cancel deletion, then confirm deletion.
- Expected result: Test mode persists in debug builds. Valid records appear only in test history. Cancel keeps records. Confirmed deletion removes only test records. Invalid fields do not save. Turning test mode off restores real history and its unfinished draft.

- [Design](../../../design/workout/30-app-screens.md)
