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

## UI review without a device

`ScreenshotTest` renders every screen in both themes with Robolectric native graphics into `app/build/screenshots/*.png`
(run `./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'`). Look at them after any UI change, and add a capture
for new screens or states. Screens are plain composables taking state + callbacks so they can be rendered with sample
data; keep `MainActivity`/engine access in `TaskAlarmApp.kt`. Dialogs and bottom sheets open in separate windows, so
render their content composables (`TaskSheet`, `SoundSheet`, …) directly. Colors come from `TaskAlarmTheme`; use
`MaterialTheme.colorScheme` roles rather than literals. Icons are Material Symbols vector drawables (`R.drawable.ic_*`),
not the material-icons-extended library.

## Code conventions

- 2-space indent, compact Kotlin style; match the surrounding file.
- `engine/` owns scheduling, persistence and ringing; `app/` is UI. UI calls into `AlarmEngine` only.
- State is JSON in SharedPreferences (`AlarmStore`, file `task_alarm_v1`) in **device-protected storage** so it is
  readable before first unlock. Always go through `AlarmStore`/`AlarmStore.storage(context)`, never
  `context.getSharedPreferences` directly; tests clear `createDeviceProtectedStorageContext()` prefs. Every read-modify-write of the store
  happens inside `synchronized(AlarmEngine.LOCK)`. Keep new fields backward compatible (`opt*` with defaults) —
  existing installs upgrade in place and the README promises old alarms keep working.
- Alarms are scheduled with `AlarmManager.setAlarmClock`; PendingIntents are distinguished by data URI
  (`taskalarm://alarm/<id>`, `taskalarm://wake/<session>/<phase>`, `taskalarm://ringing/backstop`), all with
  request code 0.
- Task kinds: text, math, memory, barcode (answers checked by `SessionTasks.submit`) and breakout, maze, walk, light
  (interactive; UI reports completion via `AlarmEngine.complete`, which checks the challenge and `SessionTasks.minimumMs`).
  Game/sensor logic lives in `Games.kt` (pure Kotlin, `GamesTest`); boards in `TaskBoards.kt`. Sensor tasks must keep
  offering their math/text fallback (`AlarmEngine.fallback`). New per-task settings go on `TaskDraft` with `opt*` reads.
- Escalation (`Escalation.kt`, driven by `RingingService.escalationTick`) and built-in sounds (`BuiltinSounds`,
  `res/raw`, regenerate with `scripts/generate-sounds.py`) are covered by `EscalationTest`.
- Delivery safeguards (re-arm on launch, ringing backstop, fallback alert, wake locks, direct boot) are covered by
  `DeliveryReliabilityTest` and `DirectBootTest`; delivery components must stay `directBootAware` and must not
  touch credential-protected storage or `getLaunchIntentForPackage` (use `AlarmEngine.launchIntent`); keep them intact when touching `fire()`, `ring()`, `finish()` or `RingingService`.
- Engine tests drive `AlarmEngine` with an injectable clock (`AlarmEngine(context) { time }`) and Robolectric
  shadows for `AlarmManager`/notifications. Add a test for every engine behaviour change.

## Release safety

The user installs debug builds over the existing app, so every APK meant for their phone must be signed with the
dedicated key at `signing/task-alarm-debug.keystore` (certificate SHA-256 `b35e88b5…8b1014`, CN=Task Alarm Debug).
The repo is public, so the key is **never committed** (`*.keystore` is gitignored). The owner keeps it in the
private repo `Sodie-Pop/Alarm-Cock-Keystore`; the environment setup script copies it into `signing/` when that repo
is checked out alongside. Otherwise attach that repo read-only and copy `task-alarm-debug.keystore` into `signing/`
yourself. If it's missing, Gradle prints `WARNING: signing/task-alarm-debug.keystore not found` and
the APK falls back to a throwaway key that will not install over their app. Never hand over an APK signed
with the fallback key. Tests and lint don't need it. Bump `versionCode`/`versionName` in `app/build.gradle.kts` for
user-facing releases.
