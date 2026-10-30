# App Screens
Session records, home summaries, exercise history, selectable graphs and set comparisons use persistent data. Settings saves graph spacing and test mode and manages isolated test records. Recording behavior is described in the [Recording Roadmap](20-recording-roadmap.md).

## Screen List
| Screen | Roadmap item | Shows |
| --- | --- | --- |
| Home | 6 | Last session summary, exercise, weight and reps chosen before START, START, RECORDS, LAYOUT and SETTINGS |
| Workout overlay | 2 | Semi-transparent overlay above other apps: rest timer in the center as the largest element, set count, total workout time, PUSH or REST button and END |
| Records | 3 and 4 | Every logged exercise name with last date, session count and average reps |
| Exercise detail | 3 and 4 | Average reps chart, spacing toggle, set by set comparison with the previous session |
| Settings | 4 and 5 | Default chart spacing, test mode switch and test record form, entry to overlay tools |

## Screen Rules
- Visual style matches the launcher icon: black background, acid accent, bold italic headings.
- START floats the workout overlay immediately and sends the app to the background. The overlay is touch-through, so the screen behind it stays usable; only its buttons take touches.
- Exercise, weight, reps and the overlay layout are chosen before START. REST saves each set with those values. Exercise names are free text such as bcpr. To change them mid-workout, open the app, edit the values and tap RESUME.
- END saves a set that is in progress with the chosen values and closes the overlay.
- Comparison rows show this session and the previous session for the same set number. Fewer reps or longer rest is marked red; more reps or shorter rest is marked acid.
- Chart spacing defaults to one slot per session, where every slot has a dot. One slot per day leaves days without a session empty, while the line still joins the surrounding sessions.
- Every logged workout can be selected by graph point or dropdown. Multiple workouts on one date remain separate and share the same day coordinate in day spacing. Missing comparison sets display a dash; no previous workout is identified explicitly.
- Home and Records show empty states when no records exist; Home offers RESUME for an unfinished session.
- Test mode records are kept apart from real records.
- LAYOUT opens the layout editor shared with the overlay. Settings keeps the legacy overlay tools: layout reset and debug log.
- Screen text is English.

## Example
- Input: User enters bcpr, 60 kg and 10 reps on Home.
- Steps: Tap START.
- Expected result: The app moves to the background and the overlay shows a large REST timer from zero, Set 0, Time and a Push button over the home screen, which stays usable.

- [Design](../../design/workout/30-app-screens.md)
