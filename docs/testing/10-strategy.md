# Test Strategy
Run gradlew.bat testDebugUnitTest with JDK 17 and Android SDK 35. Run connectedDebugAndroidTest with an available Android device or emulator. Existing tests are app/src/test/java/com/example/workouttimer/ExampleUnitTest.kt and app/src/androidTest/java/com/example/workouttimer/ExampleInstrumentedTest.kt. Template tests do not establish gesture correctness. Use synthetic data and check both editing surfaces manually.

WorkoutTest covers timer state, reboot fallback, input validation and history ordering. WorkoutStoreInstrumentedTest uses a uniquely named database and settings file per test to verify atomic set saving, draft recovery, stale save rejection, persisted preferences and test dataset isolation. Run recording tests only on a selected emulator; the connected physical phone is not part of this verification. UI checks cover session entry, validation, END, Back, history selection and settings. Storage tests alone do not prove UI behavior. Current results and static check limitations are in [Recording Verification](scenarios/workout/20-recording-roadmap.md#verification).

## Example
- Input: App installed with overlay permission.
- Steps: Execute the selection scenarios in Dev Mode and Overlay Mode.
- Expected result: Both surfaces follow the same selection rules.

- [Scenarios](scenarios/workout/10-edit-mode.md)
- [Setup](../guides/10-development.md)
