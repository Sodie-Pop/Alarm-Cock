package com.taskalarm.engine

import android.app.AlarmManager
import android.app.Application
import com.taskalarm.app.AlarmDraft
import com.taskalarm.app.TaskDraft
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SchedulingTest {
  private lateinit var context: Application
  private lateinit var engine: AlarmEngine
  private lateinit var store: AlarmStore
  private val zone = ZoneId.systemDefault()
  // Monday 17 Nov 2025, 10:00 local.
  private var time = ZonedDateTime.of(2025, 11, 17, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

  @Before fun setup() {
    context = RuntimeEnvironment.getApplication()
    context.createDeviceProtectedStorageContext().getSharedPreferences("task_alarm_v1", 0).edit().clear().commit()
    store = AlarmStore(context)
    engine = AlarmEngine(context) { time }
  }
  private fun at(millis: Long) = java.time.Instant.ofEpochMilli(millis).atZone(zone)
  private fun nextAt(id: String) = at(store.find(id)!!.getLong("nextAt"))
  private fun weekdays(hour: String = "07") = AlarmDraft(hour = hour, minute = "00", days = (1..5).toSet(), dismissTasks = emptyList())

  @Test fun timingHonoursPauseAndSkipDates() {
    val now = ZonedDateTime.of(2025, 11, 17, 10, 0, 0, 0, zone)
    val pause = ZonedDateTime.of(2026, 1, 5, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
    assertEquals(LocalDate.of(2026, 1, 5), at(Timing.next(7, 0, (1..5).toSet(), now, pauseUntil = pause)).toLocalDate())
    assertEquals(LocalDate.of(2025, 11, 19), at(Timing.next(7, 0, (1..5).toSet(), now, skipDate = "2025-11-18")).toLocalDate())
    val longPause = ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
    assertEquals(LocalDate.of(2026, 9, 1), at(Timing.next(7, 0, emptySet(), now, pauseUntil = longPause)).toLocalDate())
  }

  @Test fun skipNextSkipsOneDateAndUndoRestoresIt() {
    val alarm = weekdays()
    engine.save(alarm.json().toString())
    assertEquals(LocalDate.of(2025, 11, 18), nextAt(alarm.id).toLocalDate())
    engine.skipNext(alarm.id)
    assertEquals("2025-11-18", store.find(alarm.id)!!.getString("skipDate"))
    assertEquals(LocalDate.of(2025, 11, 19), nextAt(alarm.id).toLocalDate())
    engine.undoSkip(alarm.id)
    assertEquals(LocalDate.of(2025, 11, 18), nextAt(alarm.id).toLocalDate())
    // A skip on Friday moves to Monday.
    time = ZonedDateTime.of(2025, 11, 21, 6, 0, 0, 0, zone).toInstant().toEpochMilli()
    engine.skipNext(alarm.id)
    assertEquals(LocalDate.of(2025, 11, 24), nextAt(alarm.id).toLocalDate())
  }

  @Test fun skipIsRejectedForOneTimeDisabledOrSnoozedAlarms() {
    val once = AlarmDraft(days = emptySet(), dismissTasks = emptyList())
    engine.save(once.json().toString())
    assertThrows(IllegalStateException::class.java) { engine.skipNext(once.id) }
    val off = weekdays().copy(enabled = false)
    engine.save(off.json().toString())
    assertThrows(IllegalStateException::class.java) { engine.skipNext(off.id) }
    val snoozing = weekdays()
    engine.save(snoozing.json().toString())
    store.put(store.find(snoozing.id)!!.put("snoozed", true))
    assertThrows(IllegalStateException::class.java) { engine.skipNext(snoozing.id) }
  }

  @Test fun pauseMovesRegularRingsButKeepsAnActiveSnoozeAndWakeChecks() {
    val alarm = weekdays().copy(wakeEnabled = true)
    engine.save(alarm.json().toString())
    val until = ZonedDateTime.of(2025, 11, 24, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
    engine.pause(alarm.id, until)
    assertEquals(LocalDate.of(2025, 11, 24), nextAt(alarm.id).toLocalDate())
    engine.pause(alarm.id, 0)
    assertEquals(LocalDate.of(2025, 11, 18), nextAt(alarm.id).toLocalDate())
    val snoozeAt = time + 5 * 60_000L
    store.put(store.find(alarm.id)!!.put("snoozed", true).put("nextAt", snoozeAt))
    store.checks(listOf(JSONObject().put("sessionId", "c1").put("alarm", alarm.json()).put("stage", "scheduled").put("dueAt", time + 1).put("deadlineAt", time + 2)))
    engine.pause(alarm.id, until)
    assertEquals(snoozeAt, store.find(alarm.id)!!.getLong("nextAt"))
    assertEquals(1, store.checks().size)
    assertThrows(IllegalArgumentException::class.java) { engine.pause(alarm.id, time - 1) }
  }

  @Test fun duplicatesRollAcrossMidnightAndNeverRingBeforeTheSource() {
    val source = AlarmDraft(label = "Late", hour = "23", minute = "50", days = setOf(1, 3), dismissTasks = listOf(TaskDraft(kind = "math")))
    engine.save(source.json().toString())
    val sourceNext = store.find(source.id)!!.getLong("nextAt")
    val ids = engine.duplicate(source.id, 3, 10)
    val copies = ids.map { store.find(it)!! }
    assertEquals(listOf("Late · 2", "Late · 3", "Late · 4"), copies.map { it.getString("label") })
    assertEquals(listOf("00:00", "00:10", "00:20"), copies.map { "${it.getString("hour")}:${it.getString("minute")}" })
    assertEquals(listOf(2, 4), copies[0].getJSONArray("days").let { d -> (0 until d.length()).map { d.getInt(it) } })
    assertTrue(copies.all { it.getLong("nextAt") > sourceNext })
    val taskIds = (copies + store.find(source.id)!!).map { it.getJSONArray("dismissTasks").getJSONObject(0).getString("id") }
    assertEquals(4, taskIds.toSet().size)
    val manager = context.getSystemService(AlarmManager::class.java)
    assertEquals(4, shadowOf(manager).scheduledAlarms.size)
    assertEquals("Late", store.find(source.id)!!.getString("label"))
  }

  @Test fun duplicateRulesAreEnforced() {
    val source = weekdays()
    engine.save(source.json().toString())
    assertThrows(IllegalArgumentException::class.java) { engine.duplicate(source.id, 0, 5) }
    assertThrows(IllegalArgumentException::class.java) { engine.duplicate(source.id, 51, 1) }
    assertThrows(IllegalArgumentException::class.java) { engine.duplicate(source.id, 24, 60) }
    assertEquals(23, engine.duplicate(source.id, 23, 60).size)
  }

  @Test fun routinesAreSavedRenamedCappedAndDeleted() {
    val draft = AlarmDraft(dismissTasks = listOf(TaskDraft(kind = "memory")), vibrate = true)
    val id = engine.saveRoutine(null, "  Gym days  ", draft.json().toString())
    assertEquals("Gym days", store.routines().single().getString("name"))
    engine.saveRoutine(id, "Gym", draft.json().toString())
    assertEquals(listOf("Gym"), store.routines().map { it.getString("name") })
    assertTrue(JSONObject(engine.state()).getJSONArray("routines").length() == 1)
    assertThrows(IllegalArgumentException::class.java) { engine.saveRoutine(null, " ", draft.json().toString()) }
    repeat(29) { engine.saveRoutine(null, "R$it", draft.json().toString()) }
    assertThrows(IllegalStateException::class.java) { engine.saveRoutine(null, "One too many", draft.json().toString()) }
    engine.deleteRoutine(id)
    assertEquals(29, store.routines().size)
  }
}
