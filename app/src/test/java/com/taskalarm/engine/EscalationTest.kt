package com.taskalarm.engine

import android.app.AlarmManager
import android.app.Application
import android.media.AudioManager
import android.os.Looper
import com.taskalarm.app.AlarmDraft
import com.taskalarm.app.SoundRef
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EscalationTest {
  private lateinit var context: Application
  private lateinit var engine: AlarmEngine
  private lateinit var store: AlarmStore
  private lateinit var audio: AudioManager
  private var time = 0L

  @Before fun setup() {
    context = RuntimeEnvironment.getApplication()
    context.createDeviceProtectedStorageContext().getSharedPreferences("task_alarm_v1", 0).edit().clear().commit()
    store = AlarmStore(context)
    audio = context.getSystemService(AudioManager::class.java)
    time = System.currentTimeMillis()
    engine = AlarmEngine(context) { time }
  }

  private fun clock(elapsed: Long) = Escalation.Clock(1_000_000L + elapsed, elapsed, 7)
  private fun session(alarm: JSONObject, startedAt: Long = 0) = JSONObject().put("alarm", alarm).put("baseAlarm", JSONObject(alarm.toString()))
    .put("action", JSONObject.NULL).also { Escalation.stamp(it, "ringStarted", clock(startedAt)) }
  private fun enabled(after: Int = 2) = AlarmDraft(escalationEnabled = true, escalationAfterMinutes = after).json()
  private fun idle(duration: Duration) = shadowOf(Looper.getMainLooper()).idleFor(duration)
  private fun front() = store.sessions().first()
  private fun ringNext(id: String) {
    time = store.find(id)!!.getLong("nextAt")
    val manager = context.getSystemService(AlarmManager::class.java)
    shadowOf(manager).scheduledAlarms.toList().forEach { it.operation?.let(manager::cancel) }
    engine.fire(id)
  }

  @Test fun unansweredAlarmEscalatesAfterTheConfiguredMinutesUnlessAnActionWasChosen() {
    val waiting = session(enabled(2))
    assertFalse(Escalation.refresh(waiting, clock(119_999)))
    assertTrue(Escalation.refresh(waiting, clock(120_000)))
    assertEquals("unanswered", waiting.getString("escalationReason"))
    assertTrue(waiting.getJSONObject("baseAlarm").getBoolean("escalationLatched"))
    val solving = session(enabled(2)).put("action", "dismiss")
    assertFalse(Escalation.refresh(solving, clock(600_000)))
    assertFalse(Escalation.refresh(session(AlarmDraft().json()), clock(600_000)))
  }

  @Test fun twoSnoozesOrAnEarlierEscalationEscalateImmediately() {
    assertEquals("snoozes", session(enabled().put("snoozeCount", 2)).also { Escalation.refresh(it, clock(0)) }.getString("escalationReason"))
    assertEquals("continued", session(enabled().put("escalationLatched", true)).also { Escalation.refresh(it, clock(0)) }.getString("escalationReason"))
    assertFalse(Escalation.refresh(session(enabled().put("snoozeCount", 1)), clock(0)))
  }

  @Test fun playlistChangesEvery45SecondsAndLoops() {
    val s = session(enabled().put("escalationLatched", true))
    Escalation.refresh(s, clock(10_000))
    assertEquals(0, Escalation.slot(s, clock(10_000 + 44_999)))
    assertEquals(1, Escalation.slot(s, clock(10_000 + 45_000)))
    assertEquals(3, Escalation.slot(s, clock(10_000 + 135_000)))
    assertEquals(1_000, Escalation.untilNextTone(s, clock(10_000 + 44_000)))
    // After a reboot the elapsed clock restarts; wall-clock age is used instead.
    assertEquals(2, Escalation.slot(s, Escalation.Clock(1_000_000L + 10_000 + 90_000, 5, 8)))
    assertEquals(listOf("Beacon", "Pulse", "Chime"), Escalation.tones(s.getJSONObject("alarm")).map { it.second })
  }

  @Test fun ringingServiceForcesMaxVolumeCyclesTonesAndRestoresVolumeAfterwards() {
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    audio.setStreamVolume(AudioManager.STREAM_ALARM, 2, 0)
    val alarm = AlarmDraft(days = emptySet(), dismissTasks = emptyList(), escalationEnabled = true, escalationAfterMinutes = 1)
    engine.save(alarm.json().toString()); ringNext(alarm.id)
    val controller = Robolectric.buildService(RingingService::class.java).create()
    controller.get().onStartCommand(null, 0, 1)
    assertTrue(front().has("ringStartedWall"))
    idle(Duration.ofSeconds(30))
    assertFalse(front().optBoolean("escalated"))
    assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    idle(Duration.ofSeconds(31))
    assertTrue(front().optBoolean("escalated"))
    assertEquals("Beacon", front().getString("escalationToneName"))
    assertEquals(max, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    audio.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0) // Turning it down is undone by the guard.
    idle(Duration.ofSeconds(45))
    assertEquals("Pulse", front().getString("escalationToneName"))
    assertEquals(max, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    controller.destroy()
    assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_ALARM))
  }

  @Test fun escalationCarriesIntoSnoozedRingsAndResetsAtTheNextRegularOccurrence() {
    val alarm = AlarmDraft(days = (1..7).toSet(), dismissTasks = emptyList(), snoozeTasks = listOf(TaskDraft()), escalationEnabled = true)
    engine.save(alarm.json().toString())
    val regular = store.find(alarm.id)!!.getLong("nextAt")
    ringNext(alarm.id)
    // Simulate the service having marked this ring escalated.
    val sessions = store.sessions()
    Escalation.stamp(sessions[0], "ringStarted", Escalation.Clock(0, 0, -99))
    store.sessions(sessions)
    val id = front().getString("sessionId")
    engine.begin(id, "snooze") // Tapping after the timeout escalates first.
    assertTrue(front().getBoolean("escalated"))
    while (store.sessions().isNotEmpty()) { val s = front(); engine.submit(id, s.getString("challengeId"), s.getString("answer")) }
    assertTrue(store.find(alarm.id)!!.getBoolean("escalationLatched"))
    ringNext(alarm.id)
    assertTrue(front().getJSONObject("alarm").getBoolean("escalationLatched"))
    engine.begin(front().getString("sessionId"), "dismiss")
    time = regular + 86_400_000L - 1000
    ringNext(alarm.id)
    assertFalse(front().getJSONObject("alarm").optBoolean("escalationLatched"))
  }

  @Test fun testEscalationStartsWithThePlaylistAndLeavesTheSavedAlarmAlone() {
    val alarm = AlarmDraft(escalationEnabled = true, escalationTones = listOf(SoundRef("builtin:chime", "Chime")))
    engine.test(alarm.copy(escalationEnabled = false).json().toString())
    engine.begin(front().getString("sessionId"), "dismiss")
    while (store.sessions().isNotEmpty()) { val s = front(); engine.submit(s.getString("sessionId"), s.getString("challengeId"), s.getString("answer")) }
    assertThrows(IllegalStateException::class.java) { engine.testEscalation(alarm.copy(escalationEnabled = false).json().toString()) }
    engine.testEscalation(alarm.json().toString())
    assertTrue(front().getBoolean("test"))
    assertTrue(front().getJSONObject("alarm").getBoolean("escalationLatched"))
    assertNull(store.find(alarm.id))
  }

  @Test fun builtinSoundsResolveToAppResourcesAndUnsupportedUrisAreRejected() {
    assertEquals("android.resource://${context.packageName}/raw/pulse", BuiltinSounds.resolve(context, "builtin:pulse").toString())
    assertEquals("content://media/x", BuiltinSounds.resolve(context, "content://media/x").toString())
    assertThrows(IllegalArgumentException::class.java) {
      engine.save(AlarmDraft(escalationEnabled = true, escalationTones = listOf(SoundRef("https://example.com/a.mp3", "Web"))).json().toString())
    }
    assertThrows(IllegalArgumentException::class.java) {
      engine.save(AlarmDraft(escalationEnabled = true, escalationTones = emptyList()).json().toString())
    }
    assertThrows(IllegalArgumentException::class.java) {
      engine.save(AlarmDraft(escalationEnabled = true, escalationAfterMinutes = 121).json().toString())
    }
  }
}
