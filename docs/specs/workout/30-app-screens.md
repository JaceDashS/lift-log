# App Screens
Session records, home summaries, exercise history, selectable graphs and set comparisons use persistent data. Settings saves graph spacing and test mode and manages isolated test records. Recording behavior is described in the [Recording Roadmap](20-recording-roadmap.md).

## Screen List
| Screen | Roadmap item | Shows |
| --- | --- | --- |
| Home | 6 | Last session summary, START, RECORDS and SETTINGS |
| Session | 2 | REST or PUSH state, rest timer, exercise, weight and reps inputs, sets of this session |
| Records | 3 and 4 | Every logged exercise name with last date, session count and average reps |
| Exercise detail | 3 and 4 | Average reps chart, spacing toggle, set by set comparison with the previous session |
| Settings | 4 and 5 | Default chart spacing, test mode switch and test record form, entry to the overlay timer |

## Screen Rules
- Visual style matches the launcher icon: black background, acid accent, bold italic headings.
- Session screen: weight keeps its value between sets; reps start empty every set. Exercise names are free text such as bcpr.
- Comparison rows show this session and the previous session for the same set number. Fewer reps or longer rest is marked red; more reps or shorter rest is marked acid.
- Chart spacing defaults to one slot per session, where every slot has a dot. One slot per day leaves days without a session empty, while the line still joins the surrounding sessions.
- Every logged workout can be selected by graph point or dropdown. Multiple workouts on one date remain separate and share the same day coordinate in day spacing. Missing comparison sets display a dash; no previous workout is identified explicitly.
- Home and Records show empty states when no records exist; Home offers RESUME for an unfinished session.
- Test mode records are kept apart from real records.
- The existing overlay timer stays reachable from Settings until the Session screen takes over its function.
- Screen text is English.

## Example
- Input: User opens the app.
- Steps: Home appears; tap START.
- Expected result: Session screen resumes a stored draft or starts REST at zero with empty inputs.

- [Design](../../design/workout/30-app-screens.md)
