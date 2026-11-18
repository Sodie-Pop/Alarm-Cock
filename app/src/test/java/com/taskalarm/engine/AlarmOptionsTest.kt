package com.taskalarm.engine

import android.app.AlarmManager
import android.app.Application
import android.os.Vibrator
import com.taskalarm.app.AlarmDraft
import com.taskalarm.app.TaskDraft
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AlarmOptionsTest {
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

  private fun clearTimers() {
    val manager = context.getSystemService(AlarmManager::class.java)
    shadowOf(manager).scheduledAlarms.toList().forEach { it.operation?.let(manager::cancel) }
  }
  private fun ringNext(id: String) { time = store.find(id)!!.getLong("nextAt"); clearTimers(); engine.fire(id) }
  private fun session() = store.sessions().first()
  private fun complete(action: String) {
    val id = session().getString("sessionId")
    engine.begin(id, action)
    while (store.sessions().firstOrNull()?.getString("sessionId") == id) {
      val s = session(); engine.submit(id, s.getString("challengeId"), s.getString("answer"))
    }
  }

  @Test fun snoozeLimitCountsCompletedSnoozesAndResetsAtTheNextRegularOccurrence() {
    val alarm = AlarmDraft(days = (1..7).toSet(), dismissTasks = emptyList(), snoozeLimit = 2)
    engine.save(alarm.json().toString())
    val regular = store.find(alarm.id)!!.getLong("nextAt")
    ringNext(alarm.id)
    assertEquals(2, AlarmEngine.snoozesLeft(session().getJSONObject("alarm")))
    complete("snooze"); ringNext(alarm.id)
    assertEquals(1, AlarmEngine.snoozesLeft(session().getJSONObject("alarm")))
    complete("snooze"); ringNext(alarm.id)
    assertEquals(0, AlarmEngine.snoozesLeft(session().getJSONObject("alarm")))
    val id = session().getString("sessionId")
    assertThrows(IllegalStateException::class.java) { engine.begin(id, "snooze") }
    assertNull(session().optString("action").takeIf { it.isNotEmpty() && it != "null" })
    complete("dismiss")
    // Next day's regular ring starts fresh.
    time = regular + 86_400_000L - 1000
    ringNext(alarm.id)
    assertEquals(2, AlarmEngine.snoozesLeft(session().getJSONObject("alarm")))
  }

  @Test fun abandoningSnoozeTasksDoesNotUseUpASnooze() {
    val alarm = AlarmDraft(days = emptySet(), dismissTasks = emptyList(), snoozeTasks = listOf(TaskDraft()), snoozeLimit = 1)
    engine.save(alarm.json().toString())
    ringNext(alarm.id)
    engine.begin(session().getString("sessionId"), "snooze")
    engine.begin(session().getString("sessionId"), "dismiss")
    assertTrue(store.sessions().isEmpty())
    assertFalse(store.find(alarm.id)!!.optBoolean("snoozed"))
  }

  @Test fun snoozeCanBeTurnedOffIncludingForTests() {
    val alarm = AlarmDraft(days = emptySet(), dismissTasks = emptyList(), allowSnooze = false, snoozeTasks = listOf(TaskDraft(kind = "barcode", barcodeMode = "specific")))
    alarm.validate() // Unused snooze tasks don't block saving.
    engine.save(alarm.json().toString())
    ringNext(alarm.id)
    assertEquals(0, AlarmEngine.snoozesLeft(session().getJSONObject("alarm")))
    assertThrows(IllegalStateException::class.java) { engine.begin(session().getString("sessionId"), "snooze") }
    complete("dismiss")
    engine.test(alarm.json().toString())
    assertThrows(IllegalStateException::class.java) { engine.begin(session().getString("sessionId"), "snooze") }
  }

  @Test fun shuffleReordersTheSessionCopyButKeepsEveryTaskAndTheSavedOrder() {
    val tasks = (1..6).map { TaskDraft(kind = if (it % 2 == 0) "text" else "math", rounds = it) }
    val alarm = AlarmDraft(days = emptySet(), dismissTasks = tasks, snoozeTasks = listOf(TaskDraft()), shuffleTasks = true)
    engine.save(alarm.json().toString())
    ringNext(alarm.id)
    val saved = tasks.map { it.id }
    var reordered = false
    repeat(10) {
      val id = session().getString("sessionId")
      engine.begin(id, "snooze"); engine.begin(id, "dismiss")
      val array = session().getJSONObject("alarm").getJSONArray("dismissTasks")
      val order = (0 until array.length()).map { array.getJSONObject(it).getString("id") }
      assertEquals(saved.toSet(), order.toSet())
      if (order != saved) reordered = true
    }
    assertTrue(reordered)
    val stored = store.find(alarm.id)!!.getJSONArray("dismissTasks")
    assertEquals(saved, (0 until stored.length()).map { stored.getJSONObject(it).getString("id") })
  }

  @Test fun harderAfterSnoozeRaisesDifficultyPerCompletedSnooze() {
    val alarm = AlarmDraft(days = emptySet(), dismissTasks = listOf(TaskDraft(kind = "math", difficulty = "easy"), TaskDraft(kind = "barcode")),
      harderAfterSnooze = true)
    engine.save(alarm.json().toString())
    ringNext(alarm.id)
    fun level() = session().getJSONObject("alarm").getJSONArray("dismissTasks").getJSONObject(0).getString("difficulty")
    assertEquals("easy", level())
    complete("snooze"); ringNext(alarm.id); assertEquals("medium", level())
    complete("snooze"); ringNext(alarm.id); assertEquals("hard", level())
    complete("snooze"); ringNext(alarm.id); assertEquals("hard", level())
    assertEquals("easy", store.find(alarm.id)!!.getJSONArray("dismissTasks").getJSONObject(0).getString("difficulty"))
  }

  @Test fun randomWakeDelayFallsInsideTheConfiguredRange() {
    val alarm = AlarmDraft(days = emptySet(), dismissTasks = emptyList(), wakeEnabled = true, wakeRandomDelay = true,
      wakeDelayMinMinutes = 4, wakeDelayMaxMinutes = 6)
    val seen = mutableSetOf<Long>()
    repeat(12) {
      engine.save(alarm.json().toString())
      ringNext(alarm.id)
      val dismissedAt = time
      complete("dismiss")
      val check = store.checks().single()
      val minutes = (check.getLong("dueAt") - dismissedAt) / 60_000L
      assertTrue("delay $minutes", minutes in 4..6)
      seen += minutes
      engine.save(alarm.copy(enabled = false).json().toString())
    }
    assertTrue(seen.size > 1)
  }

  @Test fun invalidOptionValuesAreRejected() {
    assertThrows(IllegalArgumentException::class.java) { engine.save(AlarmDraft(snoozeLimit = 4).json().toString()) }
    assertThrows(IllegalArgumentException::class.java) {
      engine.save(AlarmDraft(wakeEnabled = true, wakeRandomDelay = true, wakeDelayMinMinutes = 9, wakeDelayMaxMinutes = 3).json().toString())
    }
  }

  @Test fun vibrationRunsWhileRingingAndStopsWithTheService() {
    engine.test(AlarmDraft(vibrate = true, dismissTasks = emptyList()).json().toString())
    val controller = Robolectric.buildService(RingingService::class.java).create()
    controller.get().onStartCommand(null, 0, 1)
    val vibrator = shadowOf(context.getSystemService(Vibrator::class.java))
    assertTrue(vibrator.isVibrating)
    controller.destroy()
    assertFalse(vibrator.isVibrating)
  }
}
