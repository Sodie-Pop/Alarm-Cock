# Task Alarm for Android

A native **Kotlin + Jetpack Compose** alarm clock. Users choose tasks they must complete before dismissing or snoozing an alarm. The app works locally without an account or server.

## First milestone

- Create, edit, enable, disable, and delete alarms. Choose repeat weekdays or ring once.
- Independent, ordered task lists for **dismiss** and **snooze**. Complete every selected task and round. Empty lists allow the corresponding action immediately; switching actions restarts the newly chosen sequence.
- Random text and math problems at easy, medium, and hard difficulty. Setup shows examples. Choose 1–10 rounds and up to 10 tasks per action.
- CameraX scanning with the bundled ML Kit barcode model: accept any product barcode/QR code, or enroll one specific value. Matching preserves leading zeros, case, and whitespace. No first-use model download is required.
- Android's alarm-sound picker and custom audio import via the system document picker. Imported files are copied into private app storage.
- Optional offline peak normalization: decode up to five minutes / 64 MB of PCM to a bounded temporary file, then write a WAV with its peak near −0.17 dBFS. Unsupported/oversized files fail before saving. This does not change system alarm volume; the phone's volume buttons control the alarm stream while the app is open.
- Test an alarm immediately without scheduling its draft. Test snooze ends the test.
- Native exact `AlarmManager.setAlarmClock` scheduling, looping foreground-service playback, full-screen alarm notifications, persisted task progress, stale-answer rejection, and a queue for overlapping alarms. A test does not consume a real alarm occurrence.
- Restoration of schedules after boot, package replacement, and clock/time-zone changes. Reopening the app resumes a persisted ringing session.

This is an implementation milestone. There is no attached Android phone or emulator in the cloud environment, so camera, audio, lock-screen behavior, Doze, and reboot behavior require the physical-device checklist before daily use. A successful build does not certify unattended delivery.

## Volume controls and wake-up checks (0.2.1)

In each alarm’s editor, **Protect alarm volume** blocks volume keys while the app is visible and asks the ringing service to restore the initial alarm-stream level if Android changes it. If the stream started silent, the guarded level is 50% (rounded up to the nearest available step). The original level is restored when ringing ends. This is best effort: Android can deny changes, fixed-volume devices have their own rules, and system controls/force stop remain available. See [Android AudioManager limits](https://developer.android.com/reference/android/media/AudioManager#setStreamVolume(int,int,int)).

**Allow mute while solving** shows a mute/unmute button once the user starts dismiss or snooze tasks. It silences playback without completing tasks, changing progress, or scheduling snooze. Leaving the app resumes sound; switching between dismiss and snooze also resumes sound. Both volume options default off, including for alarms created in 0.1.0.

Enable **Check that I’m out of bed** to start a wake-up cycle after dismissal, including after dismissing a failed check’s repeat alarm. Snoozing does not start the delay. The independent **Ask after dismissal** and **Time to respond** settings each default to five minutes and accept 1–1440 minutes.

Two minutes after dismissal, a notification shows **Wake-up check at [time]** using the phone’s time format. **Dismiss check** cancels the upcoming check and its re-ring deadline, including when task confirmation is configured. This option expires when the check starts; stale actions cannot bypass its tasks. Checks scheduled for two minutes or less skip the advance notice. The reminder is restored after reboot and supports checks already pending from 0.2.0.

The check uses a notification and requests a full-screen pop-up when Android allows it. Choose **Yes / No** or a separate ordered list of confirmation tasks. Yes, or completing every confirmation task before the deadline, ends the cycle. No rings immediately. An unanswered or unfinished check re-rings at its deadline. The repeat alarm reuses the original dismiss tasks by default, or uses custom repeat tasks when configured; snooze tasks stay unchanged. A repeat alarm that is dismissed starts a new check cycle until the user confirms they are up.

Check records, challenge progress, and exact native timers persist across app recreation. The advance reminder, due notification, and re-ring deadline are independently scheduled with AlarmManager, so the UI does not need to be open. Boot restoration re-arms the timers after unlock; overdue retries are delivered through an exact event rather than directly starting a media service from the boot receiver. Times are stored as wall-clock deadlines; manually changing the clock may shorten or extend a check. Editing, disabling, or deleting the source alarm cancels its pending checks. A new regular occurrence supersedes older checks for that alarm.

Use **Test wake-up check now** to open a check immediately while retaining the configured response deadline. No or timeout starts a test ring; dismissing or snoozing that test ends it without creating another check. The existing **Test sound & tasks now** does not create delayed checks.

## Flashlight alarms (0.3.0)

Each alarm has **Off**, **Steady**, and **Flashing** flashlight options. Existing alarms default to Off. Steady keeps the rear torch lit; Flashing uses a half-second on/off pulse, once per second. The ringing service controls the light independently of audio and the task screen, so mute while solving leaves it active. Dismissal or snooze stops the light; wake-up-check repeat alarms and later snoozed rings use the same setting. A pending wake-up notification does not illuminate the torch.

Android requires Camera permission for torch control. The editor provides permission and app-settings buttons; enabling or testing a flashlight alarm checks permission and hardware first. If the camera becomes unavailable during an alarm, sound and tasks continue, the task screen shows a warning, and the service retries. Barcode scanning temporarily releases the torch so CameraX can use the camera, then resumes it after scanning closes or the app leaves the scanner. The service releases its torch pulse callbacks and CPU wake lock when it stops. Lock-screen, background, camera contention, and manufacturer-specific behavior still need physical-device verification.

## Tasks, sounds and scheduling (0.5.0)

These features were rebuilt from scratch, using the earlier `feature/awake-games` branch only as a behaviour reference.

**Tasks** (any mix, in any action list, each with 1–10 rounds; the engine accepts completion only for the current challenge):

| Task | What it asks | Settings |
| --- | --- | --- |
| Math, Type text | Answer / copy exactly | Easy, Medium, Hard |
| Memory tiles | Watch 3/5/7 tiles flash, tap them back (replay any time) | Difficulty |
| Scan a barcode | Any barcode, or one you saved | Any / specific |
| Breakout | Keep a brick-breaker going; only time spent moving the paddle counts, 20 s idle resets it | Difficulty, 30 s–5 min |
| Ball maze | Roll through numbered checkpoints to the exit; layout mirrors between rounds | Difficulty, tilt or arrow buttons |
| Take a walk | Steps from the step sensor, only while the task is on screen | 10–100 steps |
| Find the light | Reach a brightness and hold it; the alarm flashlight pauses meanwhile | 100–800 lux, 3–10 s |

Interactive tasks must also take a plausible minimum time (for example a 60-second Breakout can't report done after 5 seconds). Walking, light and tilt-maze tasks offer a math or text fallback when the sensor is missing, blocked, or silent for 15 seconds; the swap applies to that ring only. Each task sheet has **Try it**, which runs the real task without touching an alarm.

**Alarm options:** allow snooze on/off; snooze limit (unlimited, 1, 2, 3, 5, 10; counted only when snooze tasks finish, reset at the next regular ring); harder after each snooze (+1 difficulty level per snooze, up to Hard); shuffle task order (per ring; every task still counts); vibrate (also while muted); moving Dismiss button (jumps every 5 s, Snooze shrinks to a corner); wake-up check at a random time within a range (default 3–7 min).

**Sounds:** built-in Beacon, Pulse and Chime (generated by `scripts/generate-sounds.py`, readable before first unlock and used as the last fallback before the beep). **Escalation:** if you don't choose Dismiss or Snooze within 1–120 minutes (default 2), or from the third ring after two snoozes, the alarm volume is held at maximum and the sound becomes a playlist that **changes every 45 seconds** and loops. The playlist holds 1–12 built-in, phone or imported sounds, can be reordered, and has a **Test escalation** button. Escalation carries into snoozed rings and wake-up retries, resets at the next regular occurrence, and the original volume is restored afterwards.

**Scheduling:** skip the next occurrence of a repeating alarm (one date; undo from the card); pause until a date; duplicate an alarm into 1–50 copies every N minutes (within 24 hours, weekdays rolled across midnight, never ringing before the original); routines (save up to 30 named task/setting sets, plus four starters; applying keeps the alarm's time, days, name and switch); Weekdays / Weekends / Every day / Once shortcuts.

**Overlaps and testing:** when more alarms queue behind the current one, or a wake-up check is waiting, the ringing screen says so; each still needs its tasks. Settings has a **delivery test** that rings a hidden test alarm in 30 seconds (lock the phone) or 2 minutes (restart it and leave it locked) and records whether it arrived.

## Settings page (0.4.1)

The gear on the home screen opens **Settings**, which lists every permission and device setting the app uses, whether it is already set, and a button that opens the right Android screen:

- **Required:** Alarms & reminders (granted automatically on Android 13+), alarm notifications, and showing over the lock screen.
- **Recommended:** battery usage (Unrestricted suggested; Restricted is flagged because it can stop alarms when the app is closed), alarm volume (muted is flagged), and wake-up check notifications.
- **Optional:** camera, for barcode tasks and the flashlight.

A summary at the top says whether alarms are ready to ring, and the gear shows a badge counting items that need attention.

## Redesigned interface (0.4.0)

The UI was rebuilt around a "night to dawn" Material 3 theme that follows the system light/dark setting; the ringing, wake-up check and scanner screens are always dark.

- **Home:** shows when the next alarm rings ("Next alarm in 7 hr 57 min"). Alarm cards show the time in the phone's 12/24-hour format, the repeat summary, and pills for dismiss tasks, snooze, wake-up check and flashlight. Tap a card to edit; the ⋮ menu has Test now and Delete. Missing permissions appear as one setup card.
- **Editor:** tap the large time to open a clock dial (or type it). Days are round toggles. Tasks are short rows; tap one to change difficulty, rounds or barcode in a bottom sheet, and **Add task** offers math, text or barcode. Sound, flashlight, volume and wake-up check settings are compact rows. **Test** and **Save alarm** stay pinned at the bottom, and saving turns the alarm on and confirms when it will ring.
- **Ringing:** a large live clock with Dismiss and Snooze anchored within thumb reach, each listing its tasks. While solving, a segmented bar shows progress across tasks and rounds; wrong answers show inline instead of in a dialog, and math uses the number keyboard.
- **Wake-up check:** a countdown ring with a large "Yes, I'm up" button (or the confirmation tasks).

Icons are Material Symbols (Apache License 2.0) stored as vector drawables in `app/src/main/res/drawable/ic_*.xml`.

## Delivery safeguards (0.4.0)

These changes target alarms that failed to sound after the app was closed:

- **Re-arm on launch.** Android cancels every scheduled alarm when an app is force-stopped (some phones also do this when an app is swiped away) and does not tell the app. Opening the app now re-registers all enabled alarms and wake-up check timers. An occurrence that became due within the last minute is still delivered; older missed occurrences move to their next valid time without ringing.
- **Ringing backstop.** While an alarm is ringing, an exact alarm stays armed about a minute ahead and restarts ringing if the process is killed. The ringing service pushes it back every 30 seconds while it is alive, and swiping the app away shortens it to five seconds. Only dismissing or snoozing cancels it. After a reboot during ringing, ringing resumes shortly after unlock.
- **Next occurrence first.** When an alarm fires, its next repeat is scheduled before ringing starts, so a failed start no longer stops a repeating alarm from future days.
- **Backup alert.** If Android refuses to start the ringing service (for example, battery usage set to Restricted), an alarm-sound notification repeats until opened. Opening it starts normal ringing.
- **Wake locks.** A short wake lock covers the hand-off from the alarm broadcast to the ringing service, and the service holds a refreshed partial wake lock for the whole ring, so the fallback beep and volume guard keep running with the screen off.
- **Rings before first unlock.** After a reboot (including Pixel overnight update restarts), alarms are restored and ring while the phone is still locked. Alarm state lives in device-protected storage (migrated automatically from older versions after the first unlock), and the receivers and ringing service are direct-boot aware. Before unlock, imported custom sounds cannot be read, so the alarm uses the phone's default alarm sound or the built-in beep, and the notification asks you to unlock to complete your tasks.
- **Exact-alarm permission.** On Android 13+ the app uses `USE_EXACT_ALARM`, which is granted at install and cannot be revoked (revoking exact-alarm access makes Android cancel all of an app's alarms). Android 12 still uses the Alarms & reminders setting.

These paths are covered by Robolectric tests but still need physical-device verification (see the checklist).

## Build and run

Use Android Studio with Java 17 or newer, Android SDK 37, and an Android 8.0+ device (API 26+). Gradle runs on Java 21 in the retained cloud environment and uses a Java 17 compiler toolchain. The project has no Node, React Native, Expo, or Metro dependency.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is standalone and embeds the native UI. From 0.4.0 it is signed with a dedicated debug key kept outside git at `signing/task-alarm-debug.keystore` (the repository is public); without that file Gradle warns and falls back to the machine's own debug key, which will not install over an existing install. It needs no development server. Release signing and Play distribution are not configured; a release build is unsigned.

For the retained cloud tools, activate `. /workspace/.onboarding/env.sh` in each shell. Its proxy helper remains outside this repository:

```bash
CI=1 python3 /workspace/.onboarding/gradle.py :app:assembleDebug :app:testDebugUnitTest --max-workers=2 --console=plain --no-daemon
```

On the phone, grant alarm notifications and full-screen alarm access using the setup card (Android 12 also needs Alarms & reminders). The card also appears in the editor so granting access preserves the draft. Grant Camera for flashlight alarms or when enrolling or completing a barcode task. Set an audible alarm volume and use **Test sound & tasks now** first.

## Project map

| Location | Responsibility |
| --- | --- |
| `app/src/main/java/com/taskalarm/app/MainActivity.kt` | Android lifecycle, lock-screen flags, system sound/document pickers, permissions |
| `app/src/main/java/com/taskalarm/app/TaskAlarmApp.kt` | Compose state owner and navigation between screens |
| `app/src/main/java/com/taskalarm/app/{Home,Editor}Screen.kt`, `RingingScreens.kt` | Alarm list, editor and sheets, ringing and wake-up check screens |
| `app/src/main/java/com/taskalarm/app/{Theme,Components,Format}.kt` | Colors and type, shared UI pieces, time/duration/task text |
| `app/src/main/java/com/taskalarm/app/AlarmDraft.kt` | Editor models and task examples |
| `app/src/main/java/com/taskalarm/app/BarcodeCamera.kt` | CameraX lifecycle and bundled ML Kit scanning |
| `app/src/main/java/com/taskalarm/engine/` | Scheduling, persistence, task checking, sound processing, ringing service |
| `app/src/test/java/com/taskalarm/engine/` | JVM math/text/barcode matching, DST scheduling, delivery safeguards, direct boot, and PCM/WAV tests |
| `app/src/test/java/com/taskalarm/app/ScreenshotTest.kt` | Renders every screen (light and dark) to `app/build/screenshots/` |
| `docs/device-testing.md` | Physical-device acceptance checklist |
| `docs/roadmap.md` | Downloadable tasks, JavaScript games, sound packs, and release work |

## Current behavior and limits

Times follow the phone's local time zone. Nonexistent spring-forward times move forward by the DST gap. Repeated fall-back times ring only at their first occurrence; once that has passed, the next valid day is selected. Missed alarms are scheduled for the next valid day during restoration. A future snooze keeps its stored timestamp across restoration.

Reboot restoration runs at locked boot and again after unlock; alarms ring before the first unlock using the default sound. An active ringing session is preserved across reboot and resumes through an exact alarm shortly after boot. Process death while ringing is recovered by the sticky foreground service and the ringing backstop; this requires device verification. Force stop cancels scheduled alarms until the app is opened again. Power-off, alarm volume zero, and manufacturer power-management rules can still prevent delivery.

Task answers are checked by the native engine against the active session and challenge ID. Camera results are validated as values, not cryptographic proofs of scanning. This is not an anti-tampering system. Alarm definitions and in-flight sessions use a private, versioned SharedPreferences JSON store. Backup is disabled to prevent restoring sessions and private sound paths onto another phone. Unused imported sounds are retained until app data is cleared; library management and cleanup are follow-up work.

There is no backend, downloadable catalog, JavaScript game host, OTA task distribution, production signing, or published app yet. The roadmap distinguishes downloadable content from native app updates.

## Workspace verification

On 2026-10-16, `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:lintDebug` passed. All 46 tests passed, including Robolectric wake-up lifecycle, torch timing/failure, and ringing-service integration tests on API 28. Lint reported zero errors and 17 warnings, including diagnostics for the alarm-lifetime flashlight wake lock; the service releases that lock when paused or stopped. APK signature verification and ZIP integrity checks passed. The standalone universal debug APK is approximately 36 MiB and includes ARM64, ARMv7, x86, and x86_64 native libraries.

The user reported that basic ringing and task dismissal worked in 0.1.0. The volume controls, wake-up checks, advance reminders, and 0.3.0 flashlight modes still require physical-device verification, especially Doze, notification actions, and reboot recovery. 0.4.0 uses a new signing key (the original key was not available), so upgrading from 0.3.0 requires uninstalling first; later builds signed with the dedicated key install over 0.4.0.

## Browser download

Earlier builds were also available through the private [Task Alarm download page](https://task-alarm-download-58b83158.mhorlacher444.chatgpt.site). Sign in with the owning ChatGPT account if prompted. Download the ZIP, extract it with Android Files, then open the APK. The ZIP preserves the original signed APK unchanged.

The download Site source is retained at `/workspace/sites/task-alarm-download`; its `.openai/hosting.json` contains the existing project ID for future updates.
