# App Screen Scenarios
UI preview checks. All values are sample data. Verified on the Pixel 7 API 34 emulator on 2026-10-30.

## Home Launch
- Input: Fresh install.
- Steps: Open Lift Log from the launcher.
- Expected result: Home shows LIFT LOG, last session Tue, Oct 28, START, RECORDS and SETTINGS.

## Session Rest And Push
- Input: Home screen.
- Steps: Tap START, then PUSH, then REST.
- Expected result: REST shows acid 01:24 and "Rest time · runs while resting"; PUSH shows grey 00:00 and "Lifting · rest timer paused". No timer counts.

## Session Inputs
- Input: Session screen.
- Steps: Read the input card.
- Expected result: Exercise bcpr, weight 60 marked kept, reps empty marked new every set; sets 1 and 2 listed below.

## Set Comparison
- Input: Records screen.
- Steps: Tap bcpr.
- Expected result: Set 2 shows 9 / 10 with a red down 1 and rest 1:52 with a red up 0:22; set 4 shows 8 / 7 with an acid up 1 and rest 2:00 with an acid down 0:10.

## Chart Spacing
- Input: bcpr detail.
- Steps: Tap Per day, then Per session.
- Expected result: Per session spaces eight dots evenly. Per day spaces them by date with day ticks; the line still joins sessions across empty days.

## Test Mode Form
- Input: Settings screen.
- Steps: Turn on Test mode.
- Expected result: The test record form, ADD RECORD, FILL 30 DAYS RANDOMLY and Clear test records appear; turning it off hides them. Buttons do nothing yet.

## Overlay Timer Entry
- Input: Settings screen.
- Steps: Tap Overlay timer.
- Expected result: The existing overlay timer screen opens.

- [Design](../../../design/workout/30-app-screens.md)
