package com.taskalarm.engine

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.taskalarm.app.AlarmDraft
import com.taskalarm.app.TaskDraft
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowCameraCharacteristics

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InteractiveTasksTest {
  private lateinit var context: Application
  private lateinit var engine: AlarmEngine
  private lateinit var store: AlarmStore
  private var time = 0L

  @Before fun setup() {
    context = RuntimeEnvironment.getApplication()
    context.createDeviceProtectedStorageContext().getSharedPreferences("task_alarm_v1", 0).edit().clear().commit()
    store = AlarmStore(context)
    time = System.currentTimeMillis()
    engine = AlarmEngine(context) { time }
  }

  private fun ring(vararg tasks: TaskDraft): String {
    val alarm = AlarmDraft(days = emptySet(), dismissTasks = tasks.toList())
    engine.save(alarm.json().toString())
    time = store.find(alarm.id)!!.getLong("nextAt")
    val manager = context.getSystemService(AlarmManager::class.java)
    shadowOf(manager).scheduledAlarms.toList().forEach { it.operation?.let(manager::cancel) }
    engine.fire(alarm.id)
    val id = store.sessions().first().getString("sessionId")
    engine.begin(id, "dismiss")
    return id
  }
  private fun session() = store.sessions().first()
  private fun challenge() = session().getString("challengeId")

  @Test fun memorySequencesMatchDifficultyAndAreCheckedExactly() {
    for ((level, length) in listOf("easy" to 3, "medium" to 5, "hard" to 7)) {
      val generated = Tasks.generate("memory", level)
      assertEquals(length, generated.answer.length)
      assertTrue(generated.answer.all { it in '1'..'4' })
    }
    val id = ring(TaskDraft(kind = "memory", difficulty = "medium", rounds = 2))
    val sequence = session().getString("sequence")
    val active = JSONObject(engine.state()).getJSONObject("active")
    assertEquals(sequence, active.getString("sequence"))
    assertFalse(active.has("answer"))
    assertFalse(engine.submit(id, challenge(), sequence.reversed() + "9"))
    assertTrue(engine.submit(id, challenge(), sequence))
    assertEquals(2, session().getInt("round"))
    assertNotEquals("", session().getString("sequence"))
  }

  @Test fun breakoutCompletesOnlyAfterItsPlayTimeAndRejectsTypedAnswers() {
    val id = ring(TaskDraft(kind = "breakout", gameSeconds = 30))
    assertFalse(engine.submit(id, challenge(), "anything"))
    time += 10_000
    assertThrows(IllegalStateException::class.java) { engine.complete(id, challenge()) }
    time += 20_000
    assertTrue(engine.complete(id, challenge()))
    assertTrue(store.sessions().isEmpty())
  }

  @Test fun completionNeedsTheCurrentChallengeAndAnInteractiveTask() {
    val id = ring(TaskDraft(kind = "walk", stepTarget = 20, rounds = 2), TaskDraft(kind = "math"))
    val first = challenge()
    time += 4_000
    assertThrows("20 steps can't take under 5 s", IllegalStateException::class.java) { engine.complete(id, first) }
    time += 1_000
    engine.complete(id, first)
    assertThrows("stale challenge", IllegalStateException::class.java) { engine.complete(id, first) }
    time += 5_000; engine.complete(id, challenge())
    assertEquals("math", session().getString("kind"))
    assertThrows(IllegalStateException::class.java) { engine.complete(id, challenge()) }
  }

  @Test fun sensorFallbackSwapsOnlyThisRingsCopyAndCoversRemainingRounds() {
    val walk = TaskDraft(kind = "walk", rounds = 2, fallbackKind = "text", difficulty = "medium")
    val id = ring(walk)
    engine.fallback(id, challenge())
    assertEquals("text", session().getString("kind"))
    assertEquals(10, session().getString("answer").length)
    engine.submit(id, challenge(), session().getString("answer"))
    assertEquals("text", session().getString("kind"))
    assertEquals(2, session().getInt("round"))
    val saved = store.alarms().single().getJSONArray("dismissTasks").getJSONObject(0)
    assertEquals("walk", saved.getString("kind"))
  }

  @Test fun tasksWithoutASensorHaveNoFallback() {
    val id = ring(TaskDraft(kind = "breakout"))
    assertThrows(IllegalStateException::class.java) { engine.fallback(id, challenge()) }
  }

  @Test fun lightAndMazeMinimumsFollowTheirSettings() {
    val id = ring(TaskDraft(kind = "light", lightHoldSeconds = 5), TaskDraft(kind = "maze", controlMode = "touch"))
    time += 4_999
    assertThrows(IllegalStateException::class.java) { engine.complete(id, challenge()) }
    time += 1; engine.complete(id, challenge())
    assertEquals("maze", session().getString("kind"))
    time += 3_000; engine.complete(id, challenge())
    assertTrue(store.sessions().isEmpty())
  }

  @Test fun wakeUpChecksAcceptInteractiveTasks() {
    val alarm = AlarmDraft(days = emptySet(), dismissTasks = emptyList(), wakeEnabled = true, wakeMode = "tasks",
      wakeTasks = listOf(TaskDraft(kind = "maze", controlMode = "tilt")))
    engine.testWake(alarm.json().toString())
    val check = store.checks().single()
    val id = check.getString("sessionId")
    assertEquals("maze", check.getString("kind"))
    engine.fallbackWake(id, check.getString("challengeId"))
    val swapped = store.checks().single()
    assertEquals("math", swapped.getString("kind"))
    assertTrue(engine.submitWake(id, swapped.getString("challengeId"), swapped.getString("answer")))
    assertTrue(store.checks().isEmpty())
  }

  @Test fun invalidTaskSettingsAreRejected() {
    for (task in listOf(TaskDraft(kind = "breakout", gameSeconds = 5), TaskDraft(kind = "walk", stepTarget = 300),
      TaskDraft(kind = "light", lightLux = 10), TaskDraft(kind = "light", lightHoldSeconds = 60), TaskDraft(kind = "maze", controlMode = "voice"),
      TaskDraft(kind = "walk", fallbackKind = "barcode"), TaskDraft(kind = "photo"))) {
      assertThrows(task.toString(), IllegalArgumentException::class.java) { engine.save(AlarmDraft(dismissTasks = listOf(task)).json().toString()) }
    }
  }

  @Test fun alarmFlashlightPausesDuringALightTask() {
    shadowOf(context).grantPermissions(Manifest.permission.CAMERA)
    shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_FLASH, true)
    val cameras = shadowOf(context.getSystemService(CameraManager::class.java))
    val back = ShadowCameraCharacteristics.newCameraCharacteristics()
    shadowOf(back).set(CameraCharacteristics.FLASH_INFO_AVAILABLE, true)
    shadowOf(back).set(CameraCharacteristics.LENS_FACING, CameraCharacteristics.LENS_FACING_BACK)
    cameras.addCamera("back", back)
    engine.test(AlarmDraft(flashlightMode = "steady", dismissTasks = listOf(TaskDraft(kind = "light"), TaskDraft(kind = "math"))).json().toString())
    val controller = Robolectric.buildService(RingingService::class.java).create()
    controller.get().onStartCommand(null, 0, 1)
    assertTrue(cameras.getTorchMode("back"))
    val id = session().getString("sessionId")
    engine.begin(id, "dismiss")
    controller.get().onStartCommand(null, 0, 2)
    assertFalse(cameras.getTorchMode("back"))
    time += 3_000; engine.complete(id, challenge())
    controller.get().onStartCommand(null, 0, 3)
    assertTrue(cameras.getTorchMode("back"))
    controller.destroy()
  }
}
