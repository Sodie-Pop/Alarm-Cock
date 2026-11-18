package com.taskalarm.engine

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import com.taskalarm.app.AlarmDraft
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm
import org.robolectric.shadows.ShadowPowerManager

/** Safeguards that keep an alarm sounding when the app is closed, force-stopped, or killed while ringing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DeliveryReliabilityTest {
  private lateinit var context: Application
  private lateinit var engine: AlarmEngine
  private lateinit var store: AlarmStore
  private lateinit var manager: AlarmManager
  private var time = 0L

  @Before fun setup() {
    context = RuntimeEnvironment.getApplication()
    context.createDeviceProtectedStorageContext().getSharedPreferences("task_alarm_v1", 0).edit().clear().commit()
    ShadowPowerManager.clearWakeLocks()
    store = AlarmStore(context)
    manager = context.getSystemService(AlarmManager::class.java)
    time = System.currentTimeMillis()
    engine = AlarmEngine(context) { time }
  }

  private fun scheduled(): List<ScheduledAlarm> = shadowOf(manager).scheduledAlarms.toList()
  private fun ScheduledAlarm.data() = operation?.let { shadowOf(it).savedIntent.data }
  private fun backstop() = scheduled().filter { it.data()?.host == "ringing" }
  private fun timer(id: String) = scheduled().filter { it.data()?.host == "alarm" && it.data()?.lastPathSegment == id }
  /** Force stop, reboot, and delivery all leave no platform timers behind for these PendingIntents. */
  private fun clearPlatformTimers() = scheduled().forEach { it.operation?.let(manager::cancel) }
  private fun fire(alarm: AlarmDraft, using: AlarmEngine = engine) {
    using.save(alarm.json().toString())
    time = store.find(alarm.id)!!.getLong("nextAt")
    clearPlatformTimers()
    using.fire(alarm.id)
  }
  private fun drainStartedServices() { while (shadowOf(context).nextStartedService != null) Unit }

  @Test fun appLaunchRearmsAlarmsThatForceStopCancelled() {
    val alarm = AlarmDraft(dismissTasks = emptyList())
    engine.save(alarm.json().toString())
    val nextAt = store.find(alarm.id)!!.getLong("nextAt")
    clearPlatformTimers()
    assertTrue(timer(alarm.id).isEmpty())
    engine.rearm()
    assertEquals(nextAt, timer(alarm.id).single().triggerAtTime)
    assertEquals(nextAt, store.find(alarm.id)!!.getLong("nextAt"))
  }

  @Test fun rearmAtTheAlarmMinuteStillDeliversInsteadOfSkippingToTomorrow() {
    val alarm = AlarmDraft(days = emptySet(), dismissTasks = emptyList())
    engine.save(alarm.json().toString())
    val nextAt = store.find(alarm.id)!!.getLong("nextAt")
    time = nextAt + 10_000
    clearPlatformTimers()
    engine.rearm()
    // A past trigger time is delivered immediately by AlarmManager.
    assertEquals(nextAt, timer(alarm.id).single().triggerAtTime)
    engine.fire(alarm.id)
    assertEquals(alarm.id, store.sessions().single().getJSONObject("alarm").getString("id"))
  }

  @Test fun rearmMovesALongMissedAlarmToItsNextOccurrenceWithoutRinging() {
    val alarm = AlarmDraft(days = (1..7).toSet(), dismissTasks = emptyList())
    engine.save(alarm.json().toString())
    time = store.find(alarm.id)!!.getLong("nextAt") + 2 * 3_600_000L
    clearPlatformTimers()
    engine.rearm()
    val moved = store.find(alarm.id)!!.getLong("nextAt")
    assertTrue(moved > time)
    assertEquals(moved, timer(alarm.id).single().triggerAtTime)
    assertTrue(store.sessions().isEmpty())
  }

  @Test fun rearmLeavesDisabledAlarmsUnscheduled() {
    val alarm = AlarmDraft(enabled = false, dismissTasks = emptyList())
    engine.save(alarm.json().toString())
    engine.rearm()
    assertTrue(timer(alarm.id).isEmpty())
  }

  @Test fun ringingArmsABackstopThatOnlyFinishingCancels() {
    val alarm = AlarmDraft(days = emptySet(), dismissTasks = emptyList())
    fire(alarm)
    assertEquals(time + AlarmEngine.BACKSTOP_MS, backstop().single().triggerAtTime)
    assertEquals(RingingService::class.java.name, shadowOf(context).nextStartedService.component!!.className)
    engine.begin(store.sessions().single().getString("sessionId"), "dismiss")
    assertTrue(store.sessions().isEmpty())
    assertTrue(backstop().isEmpty())
  }

  @Test fun backstopRestartsRingingOnlyWhileAnAlarmIsActive() {
    val alarm = AlarmDraft(days = emptySet(), dismissTasks = emptyList())
    fire(alarm)
    drainStartedServices()
    engine.backstop()
    assertEquals(RingingService::class.java.name, shadowOf(context).nextStartedService.component!!.className)
    engine.begin(store.sessions().single().getString("sessionId"), "dismiss")
    drainStartedServices()
    engine.backstop()
    assertNull(shadowOf(context).nextStartedService)
  }

  @Test fun repeatingAlarmIsRescheduledAndStillAlertsWhenTheServiceCannotStart() {
    val blocked = object : ContextWrapper(context) {
      override fun startForegroundService(service: Intent): ComponentName? = throw IllegalStateException("FGS start not allowed")
    }
    val alarm = AlarmDraft(label = "Work", days = (1..7).toSet(), dismissTasks = emptyList())
    fire(alarm, AlarmEngine(blocked) { time })
    val firedAt = time
    val next = store.find(alarm.id)!!.getLong("nextAt")
    assertTrue(next > firedAt)
    assertEquals(next, timer(alarm.id).single().triggerAtTime)
    assertEquals(1, store.sessions().size)
    val fallback = shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(FallbackAlert.ID)
    assertNotNull(fallback)
    assertEquals("Work", fallback.extras.getString(Notification.EXTRA_TITLE))
    assertTrue(fallback.flags and Notification.FLAG_INSISTENT != 0)
    assertNotNull(fallback.fullScreenIntent)
    // The backstop keeps retrying the service start.
    assertEquals(1, backstop().size)
  }

  @Test fun finishingClearsTheFallbackAlert() {
    FallbackAlert.show(context, "Work")
    fire(AlarmDraft(days = emptySet(), dismissTasks = emptyList()))
    engine.begin(store.sessions().single().getString("sessionId"), "dismiss")
    assertNull(shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(FallbackAlert.ID))
  }

  @Test fun rebootDuringRingingResumesThroughAnExactAlarm() {
    fire(AlarmDraft(days = emptySet(), dismissTasks = emptyList()))
    clearPlatformTimers()
    engine.restore(Intent.ACTION_BOOT_COMPLETED)
    assertEquals(time + 1000, backstop().single().triggerAtTime)
  }

  @Test fun serviceHoldsAWakeLockAndShortensTheBackstopWhenSwipedAway() {
    fire(AlarmDraft(days = emptySet(), dismissTasks = emptyList()))
    val controller = Robolectric.buildService(RingingService::class.java).create()
    val service = controller.get()
    service.onStartCommand(null, 0, 1)
    val lock = service.ringingWakeLock
    assertEquals("TaskAlarm:ringing", shadowOf(lock).tag)
    assertTrue(lock.isHeld)
    clearPlatformTimers()
    val before = System.currentTimeMillis()
    service.onTaskRemoved(null)
    val trigger = backstop().single().triggerAtTime
    assertTrue(trigger >= before + RingingService.TASK_REMOVED_BACKSTOP_MS)
    assertTrue(trigger <= System.currentTimeMillis() + RingingService.TASK_REMOVED_BACKSTOP_MS)
    controller.destroy()
    assertFalse(lock.isHeld)
    // Destroying the service while the alarm is still active must not disarm the backstop.
    assertEquals(1, backstop().size)
  }

  @Test fun serviceDestroyedAfterFinishingClearsALateBackstop() {
    fire(AlarmDraft(days = emptySet(), dismissTasks = emptyList()))
    val controller = Robolectric.buildService(RingingService::class.java).create()
    controller.get().onStartCommand(null, 0, 1)
    engine.begin(store.sessions().single().getString("sessionId"), "dismiss")
    engine.armBackstop() // A heartbeat racing with finish()
    controller.destroy()
    assertTrue(backstop().isEmpty())
  }
}
