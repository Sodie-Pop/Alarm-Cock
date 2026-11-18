package com.taskalarm.engine

import android.app.AlarmManager
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserManager
import com.taskalarm.app.AlarmDraft
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Alarms must restore and ring before the first unlock after a reboot (for example, an overnight update). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DirectBootTest {
  private lateinit var context: Application
  private var time = 0L

  @Before fun setup() {
    context = RuntimeEnvironment.getApplication()
    context.createDeviceProtectedStorageContext().getSharedPreferences(AlarmStore.NAME, 0).edit().clear().commit()
    context.deleteSharedPreferences(AlarmStore.NAME)
    time = System.currentTimeMillis()
  }

  private fun locked(value: Boolean) = shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(!value)
  private fun legacyFile() = File(context.dataDir, "shared_prefs/${AlarmStore.NAME}.xml")
  private fun writeLegacy(vararg alarms: AlarmDraft) {
    check(context.getSharedPreferences(AlarmStore.NAME, Context.MODE_PRIVATE).edit()
      .putString("alarms", JSONArray(alarms.map { it.json() }).toString()).commit())
  }
  private fun timers() = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.toList()

  @Test fun upgradeMovesCredentialStorageStateToDeviceProtectedStorage() {
    val alarm = AlarmDraft(label = "Gym")
    writeLegacy(alarm)
    assertTrue(legacyFile().exists())
    assertEquals("Gym", AlarmStore(context).find(alarm.id)!!.getString("label"))
    assertFalse(legacyFile().exists())
    assertEquals(1, AlarmStore(context).alarms().size)
  }

  @Test fun lockedPhoneReadsDeviceProtectedStateAndDefersMigrationUntilUnlock() {
    val migrated = AlarmDraft(label = "Already migrated")
    AlarmStore(context).put(migrated.json())
    context.deleteSharedPreferences(AlarmStore.NAME)
    locked(true)
    assertEquals(listOf(migrated.id), AlarmStore(context).alarms().map { it.getString("id") })
    locked(false)
    writeLegacy(AlarmDraft(label = "Stale"))
    locked(true)
    AlarmStore(context)
    assertTrue("credential storage must not be touched while locked", legacyFile().exists())
  }

  @Test fun existingDeviceProtectedStateWinsOverALeftoverLegacyFile() {
    val current = AlarmDraft(label = "Current")
    AlarmStore(context).put(current.json())
    writeLegacy(AlarmDraft(label = "Stale"))
    assertEquals(listOf("Current"), AlarmStore(context).alarms().map { it.getString("label") })
    assertFalse(legacyFile().exists())
  }

  @Test fun lockedBootRestoresAlarmsAndResumesAnInterruptedRing() {
    val engine = AlarmEngine(context) { time }
    val repeating = AlarmDraft(days = (1..7).toSet(), dismissTasks = emptyList())
    val ringing = AlarmDraft(days = emptySet(), dismissTasks = emptyList())
    engine.save(repeating.json().toString()); engine.save(ringing.json().toString())
    time = AlarmStore(context).find(ringing.id)!!.getLong("nextAt")
    engine.fire(ringing.id)
    val manager = context.getSystemService(AlarmManager::class.java)
    timers().forEach { it.operation?.let(manager::cancel) }
    locked(true)
    AlarmEngine(context) { time }.restore(Intent.ACTION_LOCKED_BOOT_COMPLETED)
    val data = timers().mapNotNull { it.operation?.let { op -> shadowOf(op).savedIntent.data } }
    assertTrue(data.any { it.host == "alarm" && it.lastPathSegment == repeating.id })
    assertTrue(data.any { it.host == "ringing" })
  }

  @Test fun readinessReportsBatteryRestrictionAndAlarmVolume() {
    val readiness = AlarmEngine(context).readiness()
    for (key in listOf("batteryUnrestricted", "backgroundRestricted", "alarmVolume", "alarmVolumeMax", "exactAlarmsAutomatic", "fullScreenAutomatic")) {
      assertTrue(key, readiness.has(key))
    }
    assertTrue(readiness.getInt("alarmVolumeMax") > 0)
  }

  @Test fun launchIntentNamesTheActivityExplicitly() {
    val intent = AlarmEngine.launchIntent(context)
    assertEquals(ComponentName(context, "com.taskalarm.app.MainActivity"), intent.component)
    assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
  }

  @Test fun deliveryComponentsAreDirectBootAwareAndListenForLockedBoot() {
    val pm = context.packageManager
    for (name in listOf("AlarmReceiver", "WakeCheckReceiver", "RestoreReceiver")) {
      assertTrue(name, pm.getReceiverInfo(ComponentName(context, "com.taskalarm.engine.$name"), 0).directBootAware)
    }
    assertTrue(pm.getServiceInfo(ComponentName(context, RingingService::class.java), 0).directBootAware)
    val receivers = pm.queryBroadcastReceivers(Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED).setPackage(context.packageName), PackageManager.GET_META_DATA)
    assertTrue(receivers.any { it.activityInfo.name == RestoreReceiver::class.java.name })
  }
}
