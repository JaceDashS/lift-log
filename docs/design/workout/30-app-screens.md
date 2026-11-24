# App Screens Design
Kotlin paths are relative to app/src/main/java/com/example/workouttimer. All screens use Theme.LiftLog. WorkoutStore supplies records to Home, Session, Records and ExerciseDetail; Records controls chart spacing. Debug settings control the active real or test dataset.

## Navigation
HomeActivity is the launcher activity. START requests overlay permission if needed, creates or resumes a draft, starts OverlayService and moves to the device home screen. RECORDS opens RecordsActivity. SETTINGS opens SettingsActivity, where LAYOUT opens WorkoutActivity. A records row opens ExerciseDetailActivity with the exercise name.

```mermaid
flowchart LR
 Home["HomeActivity"] --> Overlay["OverlayService"]
 Settings --> Layout["WorkoutActivity layout editor"]
 Home --> Records["RecordsActivity"]
 Home --> Settings["SettingsActivity"]
 Records --> Detail["ExerciseDetailActivity"]
```

## Public Modules
| Module | Input | Output |
| --- | --- | --- |
| ui/HomeActivity.kt | Launcher intent and active dataset | Latest stored summary, START or RESUME, RECORDS and SETTINGS |
| OverlayService.kt | Active draft from WorkoutStore | Touch-through central timer, workout counters, phase and end actions, and separate OverlaySetInputs window |
| ui/SessionActivity.kt | Persistent draft | Full-screen REST and PUSH screen; no longer opened from Home |
| ui/RecordsActivity.kt | Active dataset | One item_exercise_row per recorded name with latest date, workout count and mean repetitions |
| ui/ExerciseDetailActivity.kt | EXTRA_EXERCISE exercise name | Actual summary and trend; selected workout compared with its predecessor; selection restored on rotation |
| ui/SettingsActivity.kt | Stored preferences and manual test fields | Persistent spacing and dataset switch, validated test records, background sample generation and confirmed test deletion |
| ui/TrendChartView.kt | ExerciseTrend, visible metrics, spacing and selection | Horizontally scrollable Canvas chart with a fixed percentage axis. PER_SESSION spaces workouts evenly. PER_DAY spaces them by calendar date |
| ui/SetRows.kt | Container and stored sets | bindSetRows inflates item_set_row, numbering sets per exercise |

## Resources
- Layouts: activity_home, activity_session, activity_records, activity_exercise_detail, activity_settings, item_set_row, item_exercise_row, item_compare_row.
- Values: lift_colors.xml, lift_dimens.xml, lift_styles.xml, lift_strings.xml. Selector colors are in res/color.
- OverlayService reuses activity_workout through overlay_workout. Set reset and Edit buttons stay hidden in the overlay; The phase button shows Start set while resting and Start rest while lifting. The service declares the specialUse foreground service type required by target SDK 35.
- Settings input IDs are input_test_date, input_test_exercise, input_test_weight, input_test_reps and input_test_rest. Dates accept YYYY-MM-DD in 1900 through 2100; rest accepts seconds or minutes:seconds through 24 hours. Test writes run on Dispatchers.IO with controls disabled until completion. Only repetitions clear after a successful manual insert.
- Test mode, chart spacing and real or test drafts survive process restart. A test switch never deletes either dataset; clear test records requires a dialog and preserves real records.

## Representative Scenario
- Input: Stored bcpr workouts on days 0 and 7; latest set has 9 reps and 112 seconds rest, preceding set has 10 reps and 90 seconds rest.
- Steps: Open detail, tap Per day, then select the latest point or workout in the dropdown.
- Expected result: The line spans six empty days; comparison shows 9 versus 10, 1 fewer rep, and 22 seconds more rest. Missing set partners show a dash instead of a fabricated zero. Weights are shown for both sets.

- [Specification](../../specs/workout/30-app-screens.md)
- [Architecture](../../architecture/10-overview.md)
- [Scenarios](../../testing/test-cases/workout/30-app-screens.md)
