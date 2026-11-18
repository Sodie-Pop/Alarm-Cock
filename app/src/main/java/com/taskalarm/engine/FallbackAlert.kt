package com.taskalarm.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat

/**
 * Last-resort alert used when Android refuses to start [RingingService] (for example, battery "Restricted" mode).
 * The notification plays the alarm sound on the alarm stream and repeats it until opened or cancelled; opening it
 * launches the app, which starts the ringing service from the foreground.
 */
internal object FallbackAlert {
  const val CHANNEL = "task-alarm-fallback-v1"
  const val ID = 1002

  fun show(context: Context, label: String) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(CHANNEL, "Alarm backup alert", NotificationManager.IMPORTANCE_HIGH).apply {
      description = "Sounds your alarm if the ringing screen could not start"
      setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
      lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    })
    val open = PendingIntent.getActivity(context, 0, AlarmEngine.launchIntent(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(context, CHANNEL)
      .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(label)
      .setContentText("Your alarm is ringing. Open to complete your tasks.")
      .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(true)
      .setContentIntent(open).setFullScreenIntent(open, true).build()
    notification.flags = notification.flags or Notification.FLAG_INSISTENT
    try { manager.notify(ID, notification) } catch (_: SecurityException) { }
  }

  fun cancel(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(ID)
}
