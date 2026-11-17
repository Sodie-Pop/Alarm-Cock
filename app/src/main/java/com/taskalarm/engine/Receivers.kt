package com.taskalarm.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

/** Keeps the CPU awake from delivery until [RingingService] holds its own wake lock; expires on its own. */
private fun holdDeliveryWakeLock(context: Context) {
  context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TaskAlarm:delivery")
    .apply { setReferenceCounted(false); acquire(30_000L) }
}

class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    holdDeliveryWakeLock(context)
    try {
      val data = intent.data ?: return
      if (data.host == "ringing") AlarmEngine(context).backstop()
      else data.lastPathSegment?.let { AlarmEngine(context).fire(it) }
    }
    catch (error: Exception) { Log.e("TaskAlarm", "Unable to deliver alarm", error) }
  }
}

class RestoreReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    try { AlarmEngine(context).restore(intent.action) }
    catch (error: Exception) { Log.e("TaskAlarm", "Unable to restore alarms", error) }
  }
}

class WakeCheckReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val path = intent.data?.pathSegments ?: return
    if (path.size != 2) return
    holdDeliveryWakeLock(context)
    try { AlarmEngine(context).wakeEvent(path[0], path[1]) }
    catch (error: Exception) { Log.e("TaskAlarm", "Unable to handle wake-up check", error) }
  }
}
