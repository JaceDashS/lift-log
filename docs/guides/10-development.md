# Development
Open this project in Android Studio with JDK 17 and Android SDK 35. Configure the SDK through Android Studio or local.properties. Run gradlew.bat testDebugUnitTest for local tests and gradlew.bat assembleDebug for an APK. Build outputs, IDE state, local settings and dependency caches are excluded from Git.

## Recording Validation
Build instrumentation with gradlew.bat assembleDebugAndroidTest. Install the debug and androidTest APKs on an explicitly selected emulator, then run the WorkoutStoreInstrumentedTest class with AndroidJUnitRunner. Specify the emulator serial on every adb command so another connected device is not targeted. Storage tests create isolated database names; they do not erase the normal workout database. For screen checks, enable Test mode before entering synthetic records, and confirm deletion only for the test dataset. Keep build, instrumentation and manual screen results separate.

## Repository
The public repository is https://github.com/JaceDashS/lift-log. The existing WorkOutTimer Gradle name and Android package remain in use.

## Example
- Input: Checkout with JDK 17 and SDK 35 available.
- Steps: Configure local SDK and run gradlew.bat assembleDebug.
- Expected result: APK appears under app/build/outputs/apk/debug.

- [Testing](../testing/10-strategy.md)
- [Architecture](../architecture/10-overview.md)
- [Release](30-release.md) — Signed production APK workflow
