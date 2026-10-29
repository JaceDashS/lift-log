# Test Strategy
Run gradlew.bat testDebugUnitTest with JDK 17 and Android SDK 35. Run connectedDebugAndroidTest with an available Android device or emulator. Existing tests are app/src/test/java/com/example/workouttimer/ExampleUnitTest.kt and app/src/androidTest/java/com/example/workouttimer/ExampleInstrumentedTest.kt. Template tests do not establish gesture correctness. Use synthetic data and check both editing surfaces manually.

## Example
- Input: App installed with overlay permission.
- Steps: Execute the selection scenarios in Dev Mode and Overlay Mode.
- Expected result: Both surfaces follow the same selection rules.

- [Scenarios](scenarios/workout/10-edit-mode.md)
- [Setup](../guides/10-development.md)
