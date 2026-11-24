# App Identity
## Launcher Name
- Input: Application resources and manifest.
- Steps: Resolve application android:label through app_name.
- Expected result: Lift Log.
- Validation: Resource and manifest inspection; APK and device verification are reported separately.

## Launcher Icon
- Input: Debug APK installed on a device with a circular icon mask.
- Steps: Open the launcher, then enable themed icons on Android 13 or later.
- Expected result: Acid italic LL on black, not cut by the mask; the themed icon shows the same LL shape.
- Validation: gradlew.bat assembleDebug succeeded on 2026-10-30; device check is manual.

- [Design](../../../design/branding/10-app-identity.md)