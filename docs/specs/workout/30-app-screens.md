# App Screens
The recording roadmap screens exist as a UI preview. They show fixed sample data; nothing is timed, recorded or stored yet. Recording behavior is still described only in the [Recording Roadmap](20-recording-roadmap.md).

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
- Test mode records are kept apart from real records.
- The existing overlay timer stays reachable from Settings until the Session screen takes over its function.
- Screen text is English.

## Example
- Input: User opens the app.
- Steps: Home appears; tap START.
- Expected result: Session screen in REST state with sample timer 01:24, exercise bcpr, weight 60 and empty reps.

- [Design](../../design/workout/30-app-screens.md)
