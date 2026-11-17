package com.taskalarm.engine

import android.content.Context
import android.os.UserManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal class AlarmStore(context: Context) {
  private val prefs = storage(context).getSharedPreferences(NAME, Context.MODE_PRIVATE)
  fun alarms(): List<JSONObject> {
    val array = JSONArray(prefs.getString("alarms", "[]"))
    return (0 until array.length()).map { array.getJSONObject(it) }
  }
  fun find(id: String) = alarms().find { it.getString("id") == id }
  fun put(alarm: JSONObject) = write(alarms().filterNot { it.getString("id") == alarm.getString("id") } + alarm)
  fun delete(id: String) = write(alarms().filterNot { it.getString("id") == id })
  private fun write(alarms: List<JSONObject>) {
    check(prefs.edit().putString("alarms", JSONArray(alarms).toString()).commit()) { "Unable to save alarms" }
  }
  fun sessions(): MutableList<JSONObject> {
    val array = JSONArray(prefs.getString("sessions", "[]"))
    return (0 until array.length()).map { array.getJSONObject(it) }.toMutableList()
  }
  fun sessions(value: List<JSONObject>) {
    check(prefs.edit().putString("sessions", JSONArray(value).toString()).commit()) { "Unable to save task progress" }
  }
  fun checks(): MutableList<JSONObject> {
    val array = JSONArray(prefs.getString("wake_checks", "[]"))
    return (0 until array.length()).map { array.getJSONObject(it) }.toMutableList()
  }
  fun checks(value: List<JSONObject>) {
    check(prefs.edit().putString("wake_checks", JSONArray(value).toString()).commit()) { "Unable to save wake-up checks" }
  }

  companion object {
    const val NAME = "task_alarm_v1"

    /**
     * Device-protected storage stays readable before the first unlock after a reboot, so alarms can be restored
     * and rung while the phone is still locked (direct boot). Imported sound files remain in credential storage;
     * before unlock, playback falls back to the default alarm sound or the beep.
     */
    fun storage(context: Context): Context {
      val device = context.createDeviceProtectedStorageContext()
      if (context.getSystemService(UserManager::class.java).isUserUnlocked) migrate(context, device)
      return device
    }

    /** Versions before 0.4.0 kept state in credential-protected storage; move it once the user has unlocked. */
    private fun migrate(context: Context, device: Context) {
      val legacy = File(context.dataDir, "shared_prefs/$NAME.xml")
      if (!legacy.exists() || legacy.canonicalFile == File(device.dataDir, "shared_prefs/$NAME.xml").canonicalFile) return
      synchronized(AlarmEngine.LOCK) {
        if (!legacy.exists()) return
        // Device-protected data is newer than any leftover legacy file; never overwrite it.
        if (device.getSharedPreferences(NAME, Context.MODE_PRIVATE).all.isEmpty()) {
          check(device.moveSharedPreferencesFrom(context, NAME)) { "Unable to migrate alarms" }
        } else context.deleteSharedPreferences(NAME)
      }
    }
  }
}
