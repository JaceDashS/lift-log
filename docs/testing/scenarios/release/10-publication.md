# Publication

## Signed Release

- Input: Version 0.1.0, versionCode 1 source and a dedicated Lift Log signing key.
- Steps: Run assembleRelease and testReleaseUnitTest, sign with apksigner, inspect badging, verify signature and alignment, calculate SHA256, and compare the published asset digest.
- Expected result: Non debuggable APK uses com.example.workouttimer, displays Lift Log with the LL launcher icon, supports API 26 and later and verifies with the expected signing certificate. Source and release tag point to the reviewed commit; only APK and checksum are uploaded.

## Unsigned Installation

- Input: Unsigned release APK.
- Steps: Build without signing and attempt installation on Android.
- Expected result: Android refuses installation; distribute a signed APK instead. This requirement was checked against Android signing documentation, not by installing the unsigned APK.

## Signing Backup And Updates

- Input: Keystore and password backup, version 0.1.0 installation and a future release with increased versionCode.
- Steps: Restore signing material in a trusted environment and sign the future release with the same key.
- Expected result: The signer matches and Android accepts an update. Source and APK alone cannot recover a lost key or password. A debug signed installation requires reinstalling to change to the release signer; uninstalling may erase local data. These future update and backup restoration cases have not been executed for 0.1.0.

## Verification

On 2026-11-12, assembleRelease, vital release lint and all six release unit tests passed with JDK 17 and SDK 35. The signed APK passed apksigner v2 and v3 verification and zipalign checks. APK badging confirmed version 0.1.0, code 1, minimum API 26, target API 35, launcher icon and no debuggable flag. Certificate SHA256 is b40ccffd759bc9592c5626b545e582587cc9dd86adda8b1b5ccc13ea82892775. No new physical device installation or full lint audit was performed for this release.

- [Release Guide](../../../guides/30-release.md)
- [Test Strategy](../../10-strategy.md)
