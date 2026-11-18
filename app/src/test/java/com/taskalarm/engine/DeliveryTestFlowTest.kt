package com.taskalarm.engine

import android.app.AlarmManager
import android.app.Application
import android.content.Intent
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DeliveryTestFlowTest {
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

  @Test fun deliveryTestIsHiddenKeepsItsExactTimeAndRecordsDeliveryAndDismissal() {
    engine.testDelivery(120)
    val alarm = store.alarms().single()
    assertEquals(time + 120_000, alarm.getLong("nextAt"))
    assertEquals(0, JSONObject(engine.state()).getJSONArray("alarms").length())
    assertEquals("scheduled", store.deliveryTest()!!.getString("status"))
    val manager = context.getSystemService(AlarmManager::class.java)
    assertEquals(time + 120_000, shadowOf(manager).scheduledAlarms.single().triggerAtTime)
    // A restart before it rings keeps the exact time rather than rounding to the minute.
    shadowOf(manager).scheduledAlarms.toList().forEach { it.operation?.let(manager::cancel) }
    engine.restore(Intent.ACTION_LOCKED_BOOT_COMPLETED)
    assertEquals(time + 120_000, store.alarms().single().getLong("nextAt"))
    time += 120_000
    engine.fire(alarm.getString("id"))
    assertTrue(store.alarms().isEmpty())
    assertEquals("delivered", store.deliveryTest()!!.getString("status"))
    val session = store.sessions().single()
    assertTrue(session.getBoolean("test"))
    engine.begin(session.getString("sessionId"), "dismiss")
    assertEquals("acknowledged", store.deliveryTest()!!.getString("status"))
    assertTrue(store.checks().isEmpty())
  }

  @Test fun onlyThirtySecondOrTwoMinuteTestsAndANewTestReplacesTheOld() {
    assertThrows(IllegalArgumentException::class.java) { engine.testDelivery(5) }
    engine.testDelivery(30); engine.testDelivery(30)
    assertEquals(1, store.alarms().size)
  }

  @Test fun queuedAlarmsAreReportedAsWaiting() {
    engine.test(com.taskalarm.app.AlarmDraft(label = "First", dismissTasks = emptyList()).json().toString())
    val second = com.taskalarm.app.AlarmDraft(label = "Second", days = emptySet(), dismissTasks = emptyList())
    engine.save(second.json().toString())
    time = store.find(second.id)!!.getLong("nextAt"); engine.fire(second.id)
    val state = JSONObject(engine.state())
    assertEquals("First", state.getJSONObject("active").getJSONObject("alarm").getString("label"))
    assertEquals("Second", state.getJSONArray("waiting").getString(0))
  }
}
