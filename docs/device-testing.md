# Physical-device acceptance checklist

No physical-device checks have been executed in the cloud environment. Record device model, Android version, build variant, app version, and results for every run. Include a recent Pixel and a Samsung device, then expand to the oldest supported Android version.

## Alarm delivery

- Schedule a one-time alarm 2–3 minutes ahead. Leave the app, turn the screen off, and confirm audible ringing and the task notification. Repeat with the app removed from recents.
- Use `adb shell dumpsys deviceidle force-idle` on a test phone to verify Doze delivery, then `adb shell dumpsys deviceidle unforce` afterward.
- Verify time/weekday recurrence, midnight boundaries, clock changes, and DST behavior. Confirm snooze schedules exactly one next occurrence and returns to weekday recurrence afterward.
- Set two simultaneous alarms; finish the first and confirm the second starts. Snooze the first and confirm the queue continues.
- Reboot with a future alarm and a future snooze; unlock before their times and confirm restoration. Separately document current unsupported behavior before first unlock.
- Kill the process without Android force stop while ringing; confirm service/audio recovery and retained task progress. Reboot while ringing and confirm the stored session resumes when opening the app.
- Revoke exact-alarm permission, notifications, and full-screen access individually. Verify readiness warnings, enabling failures, and regrant/rescheduling. Confirm no permission denial is presented as a successfully enabled alarm.

## Closed-app safeguards (0.4.0)

- Upgrade from 0.3.0 without clearing data. On Android 13+, confirm Settings → Apps → Task Alarm → Alarms & reminders shows access granted and cannot be turned off.
- Schedule an alarm 3 minutes ahead, then use Settings → Apps → Task Alarm → Force stop. Confirm it does **not** ring (expected Android behavior). Repeat, but open the app once after force stop: it must ring on time. Also check `adb shell dumpsys alarm | grep -A3 com.taskalarm` before and after opening.
- Open the app within a minute after a force-stopped alarm's time; it must ring immediately rather than skip to the next day.
- While ringing (screen off, app not visible), swipe the app from recents. Ringing must continue or resume within about 5 seconds. Then `adb shell am kill com.taskalarm.app` (or `kill` the process with `adb shell`) while ringing; ringing must resume within about a minute with task progress intact.
- Reboot while ringing and **do not unlock**; ringing must resume within seconds (default sound, "Unlock your phone" notification text). Unlock and complete the tasks.
- Schedule an alarm 5 minutes ahead using a custom imported sound, reboot, and leave the phone locked. It must ring on time with the default alarm sound or beep. Repeat after unlocking (custom sound must play). Compare `adb shell dumpsys alarm` before and after unlock.
- Upgrade from 0.3.0 without clearing data, then reboot without unlocking: note whether alarms ring (migration happens only after the app runs once unlocked). Open the app once and repeat; they must ring.
- Set Battery usage to Restricted, schedule an alarm, and close the app. Record whether normal ringing or the repeating backup notification appears; opening the notification must start normal ringing. Confirm the next repeat is still scheduled. Restore Battery usage afterward.
- Choose an imported sound, delete/rename it so playback fails, and ring with the screen off for 5+ minutes; the fallback beep must keep going.
- Dismiss and snooze alarms; confirm `adb shell dumpsys alarm` shows no leftover `taskalarm://ringing/backstop` alarm and the lock screen's next-alarm time returns to the real next alarm.

## Settings page (0.4.1)

- Open Settings from the gear. With everything granted, the summary must say alarms are ready and every row shows a green status.
- Turn off alarm notifications, set Battery usage to Restricted, and mute the alarm volume; each must show a red status, the summary and gear badge must count them, and each button must open the matching Android screen. Restore them and confirm the page updates within a second of returning.

## Tasks, sounds and scheduling (0.5.0)

- **Memory:** each difficulty shows 3/5/7 flashes; wrong input keeps the same sequence; Show again works; a new sequence each round.
- **Breakout:** the timer only advances while dragging the paddle; stop for 20 s and it resets; completes at the configured time (try 30 s).
- **Maze:** tilt mode in portrait and landscape, Set level, arrow-button mode; checkpoints only count in order; it mirrors between rounds.
- **Walking:** first use asks for Physical activity; deny it and confirm the fallback button; walk 20 steps with the screen on; steps taken with the app in the background must not count.
- **Light:** cover the sensor, then turn on a light; the hold survives steady light without readings; dimming resets it; the alarm flashlight turns off during this task and back on afterwards.
- Use **Try it** for every task type from the editor.
- **Snooze limit 2:** snooze twice (completing snooze tasks), confirm the third ring has no Snooze; tomorrow's regular ring has 2 again. Turn Allow snooze off and confirm no Snooze appears (including Test).
- **Shuffle**, **Harder after each snooze**, **Vibrate** (also while muted), **Moving Dismiss** (jumps every 5 s, never under Snooze), **random wake-up delay** (several dismissals land at different times within the range).
- **Escalation:** set 1 minute; leave the alarm ringing; confirm full volume and a sound change every 45 s through the playlist; turning volume down is undone; dismissing restores your previous volume. Try an imported sound and a phone sound in the playlist, Test escalation, and escalation carrying into a snoozed ring.
- **Built-in sounds:** pick Beacon/Pulse/Chime as the alarm sound; after a restart without unlocking, an alarm still plays a real sound (Beacon or the phone default).
- **Skip next** on a weekday alarm (card says Skipping…, the following weekday rings; Undo works). **Pause until** a date (rings from that date; Resume works). Neither may disturb an active snooze or pending wake-up check.
- **Duplicate** 5 copies every 5 minutes, and a series that crosses midnight (copies after midnight roll to the next weekday and never ring before the original).
- **Routines:** save one, apply it to another alarm (time and name stay), delete it; apply each starter.
- **Delivery test:** 30 s with the phone locked; 2 min with a restart, leaving the phone at the lock screen; Settings reports the result. Two alarms at the same minute: the second shows as waiting and rings after the first.

## Task completion

- Try easy, medium, and hard text and math, checking the examples in setup against generated task styles. Wrong answers must not advance; text case and symbols must match exactly.
- Combine multiple task types and rounds in each action. No earlier completion should skip a later task. Switch actions midway and confirm the new sequence starts at task one.
- Repeat scanning for any barcode; enroll and match a specific barcode. A different barcode must fail. Test camera denial, permanent denial, dark-room scanning, leading zeros, QR payloads with spaces, and scanner cancellation.
- Send duplicate/stale submissions through an engine integration harness; the challenge ID must reject advancement. Background/reopen midway through a task; the challenge and progress must persist.
- Test task-free dismiss/snooze, a one-time snooze, recurring snooze, and the special test-mode snooze behavior.
- Test large font, TalkBack, keyboard visibility, device Back, and use on the lock screen. Finish an alarm and confirm keep-awake/lock-screen flags are cleared.

## Audio

- Pick system alarm sounds and import MP3, AAC/M4A, WAV, and Ogg supported by the device. Rename/move the original imported document and confirm the private copy still plays.
- Normalize a quiet file, silent file, full-scale file, stereo file, and an unsupported/oversized file. Confirm bounded failure and no orphan temporary files. Verify WAV header duration, sample rate, channels, and output peak with an independent audio tool.
- Check playback volume and looping, system alarm volume zero/nonzero, DND, headphones/Bluetooth, an incoming call, competing audio focus, and a missing/unreadable sound. Verify the default sound or fallback beep plays when an imported sound cannot.
- Confirm finishing an alarm releases audio focus, media playback, and wake resources, and removes the foreground notification.

## Update readiness (future)

- Install a compatible signed content update while idle and confirm existing alarms keep ringing with their original tasks.
- Attempt an update while ringing; activation must wait. Verify rollback after a broken content update, incompatible-host rejection, offline startup, and future task/game version pinning.

## Volume guard, intentional mute, and wake-up checks

- Upgrade from 0.1.0 without clearing app data. Existing alarms must retain their time, tasks, sounds, and enabled state; the new features default off.
- Enable Protect alarm volume, test a ring, and try volume down/up/mute in the task UI, camera screen, system volume panel, lock screen, and while the app is in the background. Check Android’s DND/fixed-volume limits and the guard warning when the system rejects adjustment. Start with alarm volume zero and verify the documented 50% fallback and restoration afterward.
- Enable Allow mute while solving. The button appears only during tasks. Mute, submit wrong answers, and advance multiple rounds: progress and alarm state must remain active. Unmute, switch actions, and background the app to verify audible playback resumes. Test both media playback and an unavailable-file fallback beep.
- Enable wake-up checks with a one-minute delay and one-minute response period. Dismiss a real scheduled alarm, close the app, and confirm the check notification appears, then ignore it and confirm re-ringing at the deadline. Repeat with screen off/Doze and after process death.
- Try Yes, No, and task confirmation. Yes ends checks; No immediately rings. Partial task completion must not extend the deadline. Test custom confirmation and repeat tasks, including specific barcodes and multiple rounds.
- Snooze a failed check’s repeat alarm. It must keep its custom repeat tasks, and the saved alarm’s original dismiss configuration must remain unchanged. Dismissing the repeat starts another check cycle.
- Reboot before the check and while its response window is open, unlock, and verify timer restoration and preserved task progress. Reboot past the deadline and verify an exact recovery event re-rings after unlock.
- Confirm notification Yes/No actions work without opening the app, task-mode notifications offer no Yes bypass, and dismissing a notification never cancels its deadline. Check notification-channel denial and exact-alarm permission revocation.
- Edit, disable, or delete an alarm with a pending check; it must cancel that cycle. Check two alarms with overlapping wake-up windows and a new regular occurrence while an old check is pending.
- Use Test wake-up check now in both modes. A resulting test ring must finish without creating another delayed check or changing the real alarm’s schedule.

## Flashlight alarms

- Upgrade from 0.2.1 without clearing data. Existing alarms must default to Off and keep their other settings. Save and reopen alarms in Steady and Flashing modes.
- Grant Camera using the editor. Test denial, permanent denial, a device with no flash, and permission revocation after scheduling. Sound and tasks must continue when the torch cannot be used; the task screen must show a warning.
- Test both modes with text/math tasks, mute/unmute, app backgrounding, a locked screen, and Doze. Flashing should pulse once per second; Steady should remain lit.
- Dismiss and snooze with and without tasks. Confirm the light shuts off, pulses stop, and the CPU wake lock is released. Check queued alarms using different flashlight modes.
- Open, cancel, and complete barcode scans during a flashlight alarm. The torch must pause for scanning and resume afterward. Leave the scanner in the background and return, checking that camera ownership changes do not strand the light or scanner.
- Let a wake-up check fail and then snooze its repeat alarm. Each ringing occurrence must use its configured light; silent check notifications must not turn it on.
- Use another camera app while ringing, then close it. Confirm sound/tasks continue and the light recovers when Android allows it. Test ordinary process death and service recreation; force stop must leave the torch off.
