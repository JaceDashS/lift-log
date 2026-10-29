# Architecture Overview
HomeActivity in the ui package is the launcher. It opens the Session, Records and Settings screens, which show sample data from PreviewData; Records opens ExerciseDetailActivity, which draws TrendChartView. Settings opens MainActivity. MainActivity launches WorkoutActivity or starts OverlayService after overlay permission is granted. Both editing surfaces use OverlayPreferences, TouchAwareFrameLayout, StrokeTextView, SelectionDebugLogger and constants/AppMagicNumbers. Source files are under app/src/main/java/com/example/workouttimer. Layouts are under app/src/main/res/layout. AndroidManifest.xml registers components.

```mermaid
flowchart LR
 HomeActivity --> SessionActivity
 HomeActivity --> RecordsActivity
 RecordsActivity --> ExerciseDetailActivity
 HomeActivity --> SettingsActivity
 SettingsActivity --> MainActivity
 MainActivity --> WorkoutActivity
 MainActivity --> OverlayService
 WorkoutActivity --> OverlayPreferences
 OverlayService --> OverlayPreferences
 OverlayPreferences --> DataStore
```

## Example
- Input: User opens Lift Log and taps SETTINGS, then Overlay timer, then Dev Mode.
- Steps: HomeActivity opens SettingsActivity, which opens MainActivity, which launches WorkoutActivity.
- Expected result: Editing opens inside the app.

- [Design](../design/workout/10-edit-mode.md)
- [Data Flow](20-data-flow.md)
