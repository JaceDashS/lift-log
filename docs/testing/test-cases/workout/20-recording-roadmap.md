# Recording Roadmap Scenarios

## Rest And Push
- Input: New session, followed by 90 seconds resting and PUSH.
- Steps: Advance the monotonic clock, tap PUSH, advance time again.
- Expected result: REST grows to 90 seconds; PUSH remains at 90 seconds despite wall clock changes.
- Test: WorkoutTest restUsesMonotonicTimeAndPushFreezesIt.

## Reboot Recovery
- Input: Persisted REST draft from the previous boot.
- Steps: Restore with a new boot count.
- Expected result: Wall elapsed duration is used, clamped to zero if the clock moved backward.
- Test: WorkoutTest rebootFallsBackToWallTimeWithoutNegativeDuration.

## Set Persistence
- Input: PUSH draft with bcpr, 60 kg and 10 repetitions.
- Steps: Tap REST, navigate Back, reopen, and recreate the activity.
- Expected result: Exactly one completed set; exercise and weight retained; repetitions cleared; next REST continues. Double REST cannot duplicate the set.
- Tests: WorkoutTest restKeepsExerciseAndWeightButClearsRepetitions; WorkoutStoreInstrumentedTest.

## Input Rejection
- Input: Blank exercise, negative or nonfinite weight, zero or fractional repetitions, invalid rest text.
- Steps: Attempt to save a real set or add a test record.
- Expected result: Error on invalid fields; no set saved; input retained. Bodyweight zero kg is valid.
- Test: WorkoutTest validatesUserInputAndRestDurations.

## End And Back
- Input: Workout with one saved set and an unfinished PUSH set.
- Steps: Use Back and resume, then END; cancel, save and end, or discard the unfinished set and end.
- Expected result: Back and cancel keep the draft; save records the final validated set; discard keeps only completed sets. END on an empty REST workout leaves no history entry.

## Resumed Screen Synchronization
- Input: A session screen returns from background after its stored draft changed or ended.
- Steps: Resume the activity.
- Expected result: Refresh input and timer state from the current draft; close a screen whose workout already ended rather than writing a stale draft.

## History Ordering
- Input: bcpr workouts on October 23 and twice on October 30, entered out of order.
- Steps: Load exercise history.
- Expected result: Chronological dates, separate same day workouts, averages based on that exercise only.
- Test: WorkoutTest historySortsDatesAndKeepsSameDayWorkoutsSeparate.

## Dataset Isolation
- Input: Saved real workout and test workout.
- Steps: Switch test mode, add manual records on a date, fill a thirty day range, and clear test records after confirmation.
- Expected result: Test history is separate; real records and real active draft remain intact; test inputs accept seconds or minutes:seconds.
- Test: WorkoutStoreInstrumentedTest.

- [Design](../../../design/workout/20-recording-roadmap.md)

## Verification
- Date: 2026-10-30, MAIN-PC with Pixel 7 API 34 emulator.
- JVM: Five WorkoutTest cases passed.
- Android: Seven WorkoutStoreInstrumentedTest cases passed against unique temporary databases.
- Screen checks: Manual test record insertion and lookup, validation errors, PUSH timer freeze, END cancellation, force stop and relaunch recovery, REST save with repetitions cleared, Back and RESUME, normal END, save and END, discard and END, graph point selection, selected workout restored after rotation, sample generation, cancel and confirm test deletion, real dataset isolation, saved chart default, and legacy timer entry passed through UI automation.
- Initial verification boundaries: Reboot and wall clock changes were verified with clock inputs in JVM tests, not a physical device reboot. The initial implementation verification used only the emulator. The legacy overlay itself was not revalidated.
- Static check: the ForegroundServiceType error is fixed; OverlayService now declares the specialUse type. Without it, START crashed with MissingForegroundServiceTypeException on API 34. lintDebug still reports two older errors in OverlayService.kt, NotificationPermission and WrongConstant; APK assembly succeeds independently.

## Physical Phone Installation
- Input: User requests installation on the actual phone with mock records.
- Steps: Identify Galaxy Note20 running Android 13, install the debug APK, enable Test mode, generate thirty day sample data, open Records and bcpr detail.
- Expected result: App is installed on the physical phone; isolated test records appear in history and comparison screens.
- Verified: 2026-10-30. Installation succeeded. Test storage contains 10 workouts and 90 sets across bcpr, dl and sqt. Records shows 10 sessions for each exercise; bcpr detail shows the latest and previous workout and three set comparison rows. Test mode remains enabled. The existing real draft and real records were retained.
- Display boundary: Link to Windows hosted the app on a secondary display of the physical phone. Inputs were directed to that app display; the built in screen was showing the sharing screen. This verifies execution on physical hardware, without claiming a built in screen screenshot of the app.
