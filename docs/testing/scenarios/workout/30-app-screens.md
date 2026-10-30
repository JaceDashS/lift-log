# App Screen Scenarios
Screen checks using persistent records. Use an emulator and synthetic data for recording verification.

Verified on Pixel 7 API 34 on 2026-10-30: home empty state and RESUME, session transitions and persistence, test input and history lookup, graph selection and rotation, save and discard END paths, sample generation and deletion, dataset switch, persistent chart default and legacy timer entry. See [Recording Verification](20-recording-roadmap.md#verification) for test counts and limitations.

## Home Launch
- Input: Fresh install.
- Steps: Open Lift Log from the launcher.
- Expected result: Home shows LIFT LOG, No records yet, the Before START fields, START, RECORDS, LAYOUT and SETTINGS. A saved workout updates the summary and prefills the fields; an active draft changes START to RESUME.

## Start Floats The Overlay
- Input: Home with bcpr, 60 kg and 10 reps; overlay permission granted.
- Steps: Tap START.
- Expected result: The app moves to the background. The overlay shows the rest timer large in the center, Set 0, Time and Push and End buttons. Taps outside the buttons reach the app behind, for example opening Photos from the home screen.
- Verified: Pixel 7 API 34 emulator, 2026-10-30.

## Start Without Valid Inputs
- Input: Home with an empty exercise or reps field.
- Steps: Tap START.
- Expected result: Field errors appear and no overlay starts. Without overlay permission, the permission screen opens and START continues after returning with permission.

## Overlay Rest And Push
- Input: Running overlay resting at 1:20.
- Steps: Tap Push, wait, then tap Rest.
- Expected result: Push freezes the timer and the button becomes Rest. Rest saves one set with bcpr, 60 kg and 10 reps and the frozen rest, shows Set 1 and restarts the timer from zero. Time keeps counting from START.
- Verified: Pixel 7 API 34 emulator, 2026-10-30.

## Overlay End
- Input: Running overlay with one saved set.
- Steps: Tap End.
- Expected result: The overlay and notification close and the service stops. Home shows bcpr · 1 sets and START. If End is tapped while lifting, that set is saved first.
- Verified: Pixel 7 API 34 emulator, 2026-10-30.

## Change Exercise Mid Workout
- Input: Running overlay with bcpr.
- Steps: Open Lift Log, change the exercise to sqt, tap RESUME, then Push and Rest.
- Expected result: The overlay keeps running and the next set is saved as sqt.

## Set Comparison
- Input: Stored bcpr workouts with matching second sets: current 9 reps and 112 seconds rest, previous 10 reps and 90 seconds rest.
- Steps: Tap bcpr and select the latest workout in the dropdown or graph.
- Expected result: Set 2 shows both loads, red down 1 for reps and red up 0:22 for rest; prior rest 1:30 is visible. Unmatched sets show a dash and no delta.

## Chart Spacing
- Input: bcpr detail with workouts seven days apart.
- Steps: Tap Per day, then Per session.
- Expected result: Per session spaces dots evenly. Per day spaces them by date with day ticks; the line spans the six empty days. Empty history shows an empty state and one workout shows one dot. Workout selection survives rotation.

## Test Mode Form
- Input: Settings screen.
- Steps: Turn on Test mode, enter a date, exercise, kg, reps and rest, then ADD RECORD. Generate samples, cancel deletion, then confirm deletion.
- Expected result: Test mode and chart default persist. Valid records appear only in test history. Cancel keeps records; confirmed deletion removes only test records. Invalid fields do not save. Turning test mode off restores real history and its unfinished draft.

## Overlay Tools Entry
- Input: Settings screen.
- Steps: Tap Overlay tools.
- Expected result: The legacy tools screen opens with Dev Mode, Reset All Settings and Clear Log; Start Workout is hidden.

- [Design](../../../design/workout/30-app-screens.md)
