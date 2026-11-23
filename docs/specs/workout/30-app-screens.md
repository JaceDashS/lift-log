# App Screens
Session records, home summaries, exercise history, selectable graphs and set comparisons use persistent data. Records saves graph spacing. Recording behavior is described in the [Recording Roadmap](20-recording-roadmap.md).

## Screen List
| Screen | Roadmap item | Shows |
| --- | --- | --- |
| Home | 6 | Last session summary, START or RESUME, RECORDS and SETTINGS |
| Workout overlay | 2 | Center rest timer, workout counters and separate buttons for exercise, weight, repetitions, phase and ending |
| Records | 3 and 4 | Exercise picker, scrollable weight, reps and rest chart, selected averages, spacing toggle, set comparison and an editable set table |
| Settings | 4 and 5 | Exercise list, layout editor, developer info and feedback |

## Screen Rules
- Visual style matches the launcher icon: black background, acid accent, bold italic headings.
- START opens the floating overlay in a ready state and sends the app to the background. The first PUSH begins the workout clock. The center shows GO during a set, and the phase button switches to fluorescent REST.
- Settings > Overlay opacity updates the running overlay and the settings preview while the slider moves. During PUSH, buttons are fully opaque and the timer and counters remain at Android's maximum touch-through opacity. REST restores the configured opacity.
- Settings > Layout opens the editor directly. Button Size is the only slider in the editor. Horizontal and vertical magnets can snap in the same drag. Save layout applies the edited positions and sizes. Reset layout previews the defaults, which can then be saved.
- The default layout keeps the large timer centered. Total sets, elapsed time and the phase button share the top row. The bottom row shows Exercise, Weight and End, with End at the right edge. Each button can be moved individually in Settings > Layout. The counters use the same dark theme as the controls.
- Choose an exercise in the overlay from Settings or existing records. Select 선택 안 함 to use the timer on its own. Each completed set still counts toward the active workout's total.
- PUSH keeps the current weight and freezes the rest duration. REST opens the rep entry dialog. Confirming reps saves the set and starts the rest timer. If weight is missing, the dialog also asks for weight in kg or lb. End closes the overlay and retains saved sets.
- Rest is recorded between consecutive sets of the same exercise. The first set and the first set after changing exercises have a blank rest value and are omitted from the rest average. When an exercise changes, the timer displays a note explaining this rule.
- The chart shows three toggleable lines for workout average weight, reps and rest. Each line uses percentage change from its first recorded value. For a metric starting at zero, the first positive value is the baseline. A metric recorded as zero throughout stays at 0%.
- The chart opens at the latest workout. Drag horizontally to browse older or newer workouts, and pinch to zoom the time range. Tap a workout to see its date and actual averages below the chart. Workouts without recorded reps create a gap in the reps line.
- Comparison rows show this session and the previous session for the same set number. Fewer reps or longer rest is marked red; more reps or shorter rest is marked acid.
- Per day groups all sets of the selected exercise on each calendar date into one point. Days without a session have no dot, and the line joins the surrounding dates. Choose Per session in Records to show individual workouts at even intervals.
- Every logged workout can be selected by graph point or dropdown. Multiple workouts on one date remain separate and share the same day coordinate in day spacing. Missing comparison sets display a dash; no previous workout is identified explicitly.
- Home and Records show empty states when no records exist. Home offers RESUME for an unfinished session.
- Bench Press, Squat and Lat Pulldown are available in the exercise picker by default.
- When saved records are empty, debug builds use example data for those three exercises in Records. Release builds show the empty state.
- Settings > LAYOUT opens the layout editor shared with the overlay.
- Screen text is English.

## Example
- Tap START on Home, choose Bench Press in the overlay and enter 135 lb. Tap PUSH, then REST. Enter the reps and confirm. Records shows the saved set.

- [Design](../../design/workout/30-app-screens.md)
