# AGENTS.md

Guidance for coding agents working on Task Alarm (native Kotlin + Jetpack Compose Android alarm clock).
See `README.md` for features and the project map; this file covers how to work in the repo.

## Build and test

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain --max-workers=2
./gradlew :app:lintDebug --console=plain
```

- Needs JDK 17 (Gradle toolchain; Gradle itself runs fine on 21) and Android SDK platform 37 + build-tools 37/36.
  `local.properties` (`sdk.dir=...`) is gitignored and must exist locally.
- In the cloud environment the setup script installs the SDK at `/opt/android-sdk` and writes `local.properties`.
- Maven Central occasionally returns HTTP 429 on cold dependency downloads. That is transient: retry, and keep
  `--max-workers=2`.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Ignore README references to `/workspace/.onboarding`, `/workspace/sites` and the chatgpt.site download page;
  they belong to an older, unrelated environment.

## Verification limits

There is no phone or emulator here. Unit + Robolectric tests (`app/src/test`, pinned to `@Config(sdk = [28])`) are
the only automated check. Anything touching delivery, lock screen, Doze, reboot, audio, camera or OEM behaviour must
be called out as needing on-device verification; add cases to `docs/device-testing.md` when behaviour changes.

## Code conventions

- 2-space indent, compact Kotlin style; match the surrounding file.
- `engine/` owns scheduling, persistence and ringing; `app/` is UI. UI calls into `AlarmEngine` only.
- State is JSON in SharedPreferences (`AlarmStore`, file `task_alarm_v1`). Every read-modify-write of the store
  happens inside `synchronized(AlarmEngine.LOCK)`. Keep new fields backward compatible (`opt*` with defaults) —
  existing installs upgrade in place and the README promises old alarms keep working.
- Alarms are scheduled with `AlarmManager.setAlarmClock`; PendingIntents are distinguished by data URI
  (`taskalarm://alarm/<id>`, `taskalarm://wake/<session>/<phase>`), all with request code 0.
- Engine tests drive `AlarmEngine` with an injectable clock (`AlarmEngine(context) { time }`) and Robolectric
  shadows for `AlarmManager`/notifications. Add a test for every engine behaviour change.

## Release safety

The user installs debug builds over the existing app; the signing key must stay the same as earlier versions
(it is not in the repo). Bump `versionCode`/`versionName` in `app/build.gradle.kts` for user-facing releases.
