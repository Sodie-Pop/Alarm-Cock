package com.taskalarm.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Renders every screen to app/build/screenshots so UI changes can be reviewed without a device. Doubles as a smoke
 * test that each screen composes with realistic data in both themes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private var screen by mutableStateOf<@Composable () -> Unit>({})
  private val now = ZonedDateTime.of(2026, 11, 17, 22, 48, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()

  private val morning = AlarmDraft(id = "a1", label = "Work", hour = "06", minute = "45",
    dismissTasks = listOf(TaskDraft(kind = "math", difficulty = "medium", rounds = 3), TaskDraft(kind = "barcode", barcodeMode = "specific", barcode = "0123456789")),
    snoozeTasks = listOf(TaskDraft(kind = "text")), wakeEnabled = true, flashlightMode = "flashing")
  private val weekend = AlarmDraft(id = "a2", label = "Weekend run", hour = "08", minute = "30", days = setOf(6, 7),
    dismissTasks = listOf(TaskDraft(kind = "text", difficulty = "easy")))
  private val nap = AlarmDraft(id = "a3", label = "Nap", hour = "15", minute = "00", days = emptySet(), enabled = false, dismissTasks = emptyList())

  private fun stored(alarm: AlarmDraft, nextAt: Long?) = alarm.json().apply { if (nextAt != null) put("nextAt", nextAt) }
  private fun homeState(alarms: List<JSONObject>, readiness: JSONObject = JSONObject(), checks: List<JSONObject> = emptyList()) =
    JSONObject().put("alarms", JSONArray(alarms)).put("readiness", readiness).put("wakeChecks", JSONArray(checks))

  /** [frozen] renders one frame without idling, for screens with endless effects (e.g. the moving Dismiss button). */
  private fun capture(name: String, dark: Boolean, frozen: Boolean = false, content: @Composable () -> Unit) {
    if (frozen) compose.mainClock.autoAdvance = false
    screen = { TaskAlarmTheme(dark) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } } }
    if (frozen) repeat(5) { compose.mainClock.advanceTimeByFrame(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }
    else compose.waitForIdle()
    val view = compose.activity.window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    val file = File("build/screenshots/$name-${if (dark) "dark" else "light"}.png").apply { parentFile!!.mkdirs() }
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }
  private fun start() = compose.setContent { screen() }
  private fun noop(@Suppress("UNUSED_PARAMETER") vararg ignored: Any?) {}

  @Test @Config(qualifiers = "w412dp-h915dp-420dpi")
  fun phoneScreens() {
    start()
    val alarms = listOf(stored(morning, now + 7 * 3_600_000L + 57 * 60_000L), stored(weekend, now + 3 * 86_400_000L), stored(nap, null))
    val check = JSONObject().put("sessionId", "w1").put("label", "Work").put("dueAt", now + 300_000).put("deadlineAt", now + 600_000).put("stage", "scheduled")
    for (dark in listOf(true, false)) {
      capture("home", dark) { HomeScreen(homeState(alarms), false, SnackbarHostState(), now, {}, {}, { _, _ -> }, {}, {}, {}) }
      capture("home-setup", dark) {
        HomeScreen(homeState(alarms, JSONObject().put("notifications", false).put("fullScreen", false), listOf(check)), false, SnackbarHostState(), now, {}, {}, { _, _ -> }, {}, {}, {})
      }
      capture("home-empty", dark) { HomeScreen(homeState(emptyList()), false, SnackbarHostState(), now, {}, {}, { _, _ -> }, {}, {}, {}) }
      capture("editor", dark) {
        EditorScreen(morning, false, JSONObject().put("flashlightAvailable", true).put("cameraPermission", true), false, now,
          {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> })
      }
      capture("sheet-task", dark) {
        Box(Modifier.padding(top = 24.dp)) { TaskSheet(morning.dismissTasks[0], {}, {}, {}, {}) }
      }
      capture("sheet-barcode", dark) {
        Box(Modifier.padding(top = 24.dp)) { TaskSheet(TaskDraft(kind = "barcode", barcodeMode = "specific"), {}, {}, {}, {}) }
      }
      val good = JSONObject().put("exactAlarmsAutomatic", true).put("batteryUnrestricted", true).put("cameraPermission", true)
        .put("alarmVolume", 6).put("alarmVolumeMax", 7)
      capture("settings", dark) { SettingsScreen(good, "0.4.0", {}, {}) }
      val issues = JSONObject().put("exactAlarmsAutomatic", true).put("notifications", false).put("backgroundRestricted", true)
        .put("alarmVolume", 0).put("alarmVolumeMax", 7).put("cameraPermission", false)
      capture("settings-issues", dark) { SettingsScreen(issues, "0.4.0", {}, {}) }
      capture("sheet-add", dark) { Box(Modifier.padding(top = 24.dp)) { AddTaskSheet {} } }
      capture("sheet-sound", dark) { Box(Modifier.padding(top = 24.dp)) { SoundSheet(morning.copy(soundUri = "content://x", soundName = "Birdsong.mp3"), {}, {}, {}) } }
    }
    val ringing = JSONObject().put("sessionId", "s1").put("alarm", morning.copy(allowMuteWhileSolving = true).json()).put("action", JSONObject.NULL)
      .put("test", false).put("muted", false)
    capture("ringing", true) { RingingScreen(ringing, false, null, now, {}, {}, {}, {}, {}) }
    val solving = JSONObject(ringing.toString()).put("action", "dismiss").put("taskIndex", 0).put("round", 2).put("kind", "math")
      .put("prompt", "12 × 7 + 18 = ?").put("challengeId", "c1")
    capture("ringing-math", true) { RingingScreen(solving, false, "Not quite — try again.", now, {}, {}, {}, {}, {}) }
    val scanning = JSONObject(solving.toString()).put("taskIndex", 1).put("round", 1).put("kind", "barcode").put("prompt", "Scan your saved barcode")
    capture("ringing-barcode", true) { RingingScreen(scanning, false, null, now, {}, {}, {}, {}, {}) }
    val wake = JSONObject().put("sessionId", "w1").put("alarm", morning.json()).put("dueAt", now - 100_000).put("deadlineAt", now + 200_000)
      .put("stage", "waiting").put("test", false)
    capture("wake", true) { WakeCheckScreen(wake, false, null, now, {}, {}, {}, {}) }
    val wakeTasks = JSONObject(wake.toString()).put("alarm", morning.copy(wakeMode = "tasks", wakeTasks = listOf(TaskDraft(kind = "text", difficulty = "medium"))).json())
      .put("taskIndex", 0).put("round", 1).put("kind", "text").put("prompt", "K7mP3xRa9q").put("challengeId", "c2")
    capture("wake-tasks", true) { WakeCheckScreen(wakeTasks, false, null, now, {}, {}, {}, {}) }
    val limited = JSONObject(ringing.toString()).put("alarm", morning.copy(snoozeLimit = 3, vibrate = true).json().put("snoozeCount", 1)).put("escalated", true).put("escalationToneName", "Pulse")
    capture("ringing-limit", true) { RingingScreen(limited, false, null, now, {}, {}, {}, {}, {}) }
    val moving = JSONObject(ringing.toString()).put("alarm", morning.copy(movingDismiss = true).json())
    capture("ringing-moving", true, frozen = true) { RingingScreen(moving, false, null, now, {}, {}, {}, {}, {}) }
  }

  @Test @Config(qualifiers = "w412dp-h3000dp-420dpi")
  fun fullEditor() {
    start()
    val full = morning.copy(snoozeTasks = emptyList(), wakeMode = "tasks", wakeReuseDismissTasks = false, protectVolume = true,
      snoozeLimit = 3, harderAfterSnooze = true, vibrate = true, shuffleTasks = true, wakeRandomDelay = true, escalationEnabled = true)
    for (dark in listOf(true, false)) capture("editor-full", dark) {
      EditorScreen(full, false, JSONObject().put("flashlightAvailable", true).put("cameraPermission", false), false, now,
        {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> })
    }
  }
}
