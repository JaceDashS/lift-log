# App Screen Scenarios
Screen checks using persistent records. Use an emulator and synthetic data for recording verification.

Verified on Pixel 7 API 34 on 2026-10-30: home empty state and RESUME, session transitions and persistence, test input and history lookup, graph selection and rotation, save and discard END paths, sample generation and deletion, dataset switch, persistent chart default and legacy timer entry. See [Recording Verification](20-recording-roadmap.md#verification) for test counts and limitations.

## Home Launch
- Input: Fresh install.
- Steps: Open Lift Log from the launcher.
- Expected result: Home shows LIFT LOG, No records yet, START, RECORDS and SETTINGS. A saved workout updates the summary; an active draft changes START to RESUME.

## Session Rest And Push
- Input: Home screen.
- Steps: Tap START, then PUSH, then REST.
- Expected result: REST grows; PUSH freezes the preceding rest; REST requires valid inputs and saves one set.

## Session Inputs
- Input: Session screen.
- Steps: Enter bcpr, 60 kg and 10 reps during PUSH, then tap REST.
- Expected result: Exercise and weight retained, reps cleared, stored set listed below.

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

## Overlay Timer Entry
- Input: Settings screen.
- Steps: Tap Overlay timer.
- Expected result: The existing overlay timer screen opens.

- [Design](../../../design/workout/30-app-screens.md)
