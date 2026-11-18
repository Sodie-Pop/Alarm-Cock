package com.taskalarm.engine

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

internal class AlarmEngine(private val context: Context, private val now: () -> Long = System::currentTimeMillis) {
  private val store = AlarmStore(context)
  private val manager = context.getSystemService(AlarmManager::class.java)
  private val wakeChecks by lazy { WakeChecks(context, now, ::retryWake) }
  private fun receiver(id: String) = PendingIntent.getBroadcast(context, 0,
    Intent(context, AlarmReceiver::class.java).setData(Uri.parse("taskalarm://alarm/$id")),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  private fun backstopIntent() = PendingIntent.getBroadcast(context, 0,
    Intent(context, AlarmReceiver::class.java).setData(Uri.parse("taskalarm://ringing/backstop")),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

  fun readiness(): JSONObject {
    val audio = context.getSystemService(AudioManager::class.java)
    val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val nm = context.getSystemService(NotificationManager::class.java)
    fun channelEnabled(id: String) = nm.getNotificationChannel(id)?.importance != NotificationManager.IMPORTANCE_NONE
    return JSONObject().put("exactAlarms", Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms())
      .put("notifications", enabled && channelEnabled(RingingService.CHANNEL))
      .put("wakeNotifications", enabled && channelEnabled(WakeChecks.CHANNEL))
      .put("fullScreen", Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent())
      .put("flashlightAvailable", context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH))
      .put("cameraPermission", context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
      .put("activityPermission", Build.VERSION.SDK_INT < 29 || context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)
      // Exact-alarm access is granted at install (USE_EXACT_ALARM) on Android 13+ and cannot be revoked.
      .put("exactAlarmsAutomatic", Build.VERSION.SDK_INT >= 33)
      .put("fullScreenAutomatic", Build.VERSION.SDK_INT < 34)
      // Battery "Unrestricted" exempts the app from optimizations; "Restricted" can block the ringing service.
      .put("batteryUnrestricted", context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName))
      .put("backgroundRestricted", Build.VERSION.SDK_INT >= 28 && context.getSystemService(ActivityManager::class.java).isBackgroundRestricted)
      .put("alarmVolume", audio.getStreamVolume(AudioManager.STREAM_ALARM))
      .put("alarmVolumeMax", audio.getStreamMaxVolume(AudioManager.STREAM_ALARM))
  }

  fun state(): String = synchronized(LOCK) {
    // Reconcile missed broadcasts when the application is visible, in addition to native timers.
    wakeChecks.reconcile()
    val active = store.sessions().firstOrNull()?.let { session ->
      JSONObject(session.toString()).apply {
        remove("answer")
        remove("baseAlarm")
        val action = session.optString("action", "")
        put("totalTasks", session.getJSONObject("alarm").getJSONArray(if (action == "snooze") "snoozeTasks" else "dismissTasks").length())
      }
    }
    val pending = store.checks().map { JSONObject().put("sessionId", it.getString("sessionId"))
      .put("label", it.getJSONObject("alarm").getString("label")).put("dueAt", it.getLong("dueAt"))
      .put("deadlineAt", it.getLong("deadlineAt")).put("stage", it.getString("stage")).put("test", it.optBoolean("test")) }
    JSONObject().put("alarms", JSONArray(store.alarms())).put("active", active ?: JSONObject.NULL)
      .put("wakeCheck", wakeChecks.visible() ?: JSONObject.NULL).put("wakeChecks", JSONArray(pending))
      .put("readiness", readiness()).toString()
  }

  private fun validate(alarm: JSONObject) {
    require(alarm.getString("id").matches(Regex("[A-Za-z0-9-]{1,100}"))) { "Invalid alarm ID" }
    require(alarm.getInt("hour") in 0..23 && alarm.getInt("minute") in 0..59) { "Invalid time" }
    require(alarm.getInt("snoozeMinutes") in 1..60) { "Snooze must be 1–60 minutes" }
    require(alarm.optString("flashlightMode", "off") in listOf("off", "steady", "flashing")) { "Invalid flashlight mode" }
    require(alarm.optInt("snoozeLimit", 0) in listOf(0, 1, 2, 3, 5, 10)) { "Invalid snooze limit" }
    Escalation.validate(alarm)
    require(alarm.getString("label").length <= 120) { "Alarm name is too long" }
    val days = alarm.getJSONArray("days")
    require((0 until days.length()).all { days.getInt(it) in 1..7 })
    if (alarm.optBoolean("wakeEnabled")) {
      require(alarm.optInt("wakeDelayMinutes", 5) in 1..1440 && alarm.optInt("wakeResponseMinutes", 5) in 1..1440) { "Wake-up check times must be 1–1440 minutes" }
      require(!alarm.optBoolean("wakeRandomDelay") || (alarm.optInt("wakeDelayMinMinutes", 3) in 1..1440 &&
        alarm.optInt("wakeDelayMaxMinutes", 7) in alarm.optInt("wakeDelayMinMinutes", 3)..1440)) { "Invalid random wake-up check delay" }
      require(alarm.optString("wakeMode", "yes_no") in listOf("yes_no", "tasks"))
      require(alarm.optString("wakeMode", "yes_no") != "tasks" || (alarm.optJSONArray("wakeTasks")?.length() ?: 0) > 0) { "Choose at least one wake-up confirmation task" }
    }
    for (key in listOf("dismissTasks", "snoozeTasks", "wakeTasks", "wakeRetryTasks")) {
      val tasks = alarm.optJSONArray(key) ?: continue
      require(tasks.length() <= 10) { "Choose at most 10 tasks per action" }
      for (i in 0 until tasks.length()) {
        val task = tasks.getJSONObject(i)
        val kind = task.getString("kind")
        require(kind in listOf("text", "math", "barcode", "memory", "breakout", "maze", "walk", "light")) { "Unknown task" }
        require(task.optString("difficulty", "easy") in listOf("easy", "medium", "hard"))
        require(task.optInt("rounds", 1) in 1..10)
        if (kind == "breakout") require(task.optInt("gameSeconds", 60) in 10..3600) { "Breakout must last 10 seconds to 1 hour" }
        if (kind == "walk") require(task.optInt("stepTarget", 20) in 5..200) { "Choose 5–200 steps" }
        if (kind == "light") require(task.optInt("lightLux", 200) in 50..2000 && task.optInt("lightHoldSeconds", 3) in 2..30) { "Choose 50–2000 lux held for 2–30 seconds" }
        if (kind == "maze") require(task.optString("controlMode", "tilt") in listOf("tilt", "touch")) { "Invalid maze controls" }
        require(task.optString("fallbackKind", "math") in listOf("math", "text")) { "Fallback must be math or text" }
        if (task.getString("kind") == "barcode") {
          require(task.getString("barcodeMode") in listOf("any", "specific"))
          val used = key == "dismissTasks" || (key == "snoozeTasks" && alarm.optBoolean("allowSnooze", true)) || (alarm.optBoolean("wakeEnabled") &&
            ((key == "wakeTasks" && alarm.optString("wakeMode", "yes_no") == "tasks") || (key == "wakeRetryTasks" && !alarm.optBoolean("wakeReuseDismissTasks", true))))
          require(!used || task.getString("barcodeMode") != "specific" || task.getString("barcode").isNotEmpty()) { "Scan the target barcode first" }
        }
      }
    }
  }

  fun save(json: String) = synchronized(LOCK) {
    val alarm = JSONObject(json); validate(alarm)
    alarm.remove("snoozed"); alarm.remove("wakeRetry"); alarm.remove("snoozeCount"); alarm.remove("escalationLatched")
    require(store.sessions().none { it.getJSONObject("alarm").getString("id") == alarm.getString("id") }) { "Finish this alarm before editing it" }
    if (alarm.getBoolean("enabled")) {
      val access = readiness()
      check(access.getBoolean("exactAlarms")) { "Allow Alarms & reminders before enabling an alarm" }
      check(access.getBoolean("notifications")) { "Allow alarm notifications before enabling an alarm" }
      check(!alarm.optBoolean("wakeEnabled") || access.getBoolean("wakeNotifications")) { "Allow wake-up check notifications before enabling this alarm" }
      checkFlashlightAccess(alarm, access)
      alarm.put("nextAt", next(alarm)); schedule(alarm)
    } else { manager.cancel(receiver(alarm.getString("id"))); alarm.remove("nextAt") }
    store.put(alarm)
    wakeChecks.cancelForAlarm(alarm.getString("id"))
  }

  fun delete(id: String) = synchronized(LOCK) {
    require(store.sessions().none { it.getJSONObject("alarm").getString("id") == id }) { "Finish this alarm before deleting it" }
    manager.cancel(receiver(id)); store.delete(id); wakeChecks.cancelForAlarm(id)
  }

  private fun next(alarm: JSONObject): Long {
    val days = alarm.getJSONArray("days")
    return Timing.next(alarm.getInt("hour"), alarm.getInt("minute"), (0 until days.length()).map { days.getInt(it) }.toSet(),
      ZonedDateTime.ofInstant(Instant.ofEpochMilli(now()), ZoneId.systemDefault()))
  }
  private fun schedule(alarm: JSONObject) {
    val show = PendingIntent.getActivity(context, 0, launchIntent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    manager.setAlarmClock(AlarmManager.AlarmClockInfo(alarm.getLong("nextAt"), show), receiver(alarm.getString("id")))
  }
  fun launchIntent(): Intent = launchIntent(context)

  fun fire(id: String) = synchronized(LOCK) {
    val alarm = store.find(id) ?: return@synchronized
    if (!alarm.getBoolean("enabled") || alarm.optLong("nextAt", Long.MAX_VALUE) > now() + 2000) return@synchronized
    wakeChecks.cancelForAlarm(id)
    // A regular occurrence starts with a fresh snooze count; snoozed rings and wake-up retries keep it.
    if (!alarm.optBoolean("snoozed") && !alarm.optBoolean("wakeRetry")) { alarm.put("snoozeCount", 0); alarm.remove("escalationLatched") }
    enqueue(alarm, false, alarm.optBoolean("wakeRetry"))
    // Arm the next occurrence before starting media: a failed service start must not end a repeating alarm.
    try {
      alarm.remove("snoozed"); alarm.remove("wakeRetry")
      if (alarm.getJSONArray("days").length() > 0) { alarm.put("nextAt", next(alarm)); schedule(alarm) }
      else { alarm.put("enabled", false); alarm.remove("nextAt") }
      store.put(alarm)
    } finally { ring() }
  }

  fun test(json: String) = synchronized(LOCK) {
    check(store.sessions().isEmpty()) { "An alarm is already ringing" }
    val alarm = JSONObject(json); validate(alarm)
    checkFlashlightAccess(alarm, readiness())
    enqueue(alarm, true); ring()
  }
  private fun checkFlashlightAccess(alarm: JSONObject, access: JSONObject) {
    if (alarm.optString("flashlightMode", "off") == "off") return
    check(access.getBoolean("flashlightAvailable")) { "This phone has no flashlight. Choose Off to enable this alarm." }
    check(access.getBoolean("cameraPermission")) { "Allow camera access for the flashlight, or choose Off." }
  }
  /** Rings a test of this draft with escalation already active, so the playlist starts immediately. */
  fun testEscalation(json: String) = synchronized(LOCK) {
    check(store.sessions().isEmpty()) { "An alarm is already ringing" }
    val alarm = JSONObject(json); validate(alarm)
    check(alarm.optBoolean("escalationEnabled")) { "Turn on escalation first" }
    checkFlashlightAccess(alarm, readiness())
    enqueue(alarm.put("escalationLatched", true), true); ring()
  }
  fun testWake(json: String) = synchronized(LOCK) {
    check(store.sessions().isEmpty()) { "Finish the ringing alarm first" }
    val alarm = JSONObject(json); validate(alarm)
    check(alarm.optBoolean("wakeEnabled")) { "Enable wake-up checks first" }
    wakeChecks.schedule(alarm, true)
  }

  private fun enqueue(base: JSONObject, test: Boolean, wakeRetry: Boolean = false) {
    val alarm = JSONObject(base.toString())
    if (wakeRetry && !base.optBoolean("wakeReuseDismissTasks", true)) {
      alarm.put("dismissTasks", base.optJSONArray("wakeRetryTasks") ?: JSONArray())
    }
    if (base.optBoolean("harderAfterSnooze")) {
      val steps = base.optInt("snoozeCount", 0)
      for (key in listOf("dismissTasks", "snoozeTasks")) alarm.put(key, TaskOrder.harder(alarm.optJSONArray(key) ?: JSONArray(), steps))
    }
    val sessions = store.sessions()
    if (sessions.none { it.getJSONObject("alarm").getString("id") == alarm.getString("id") && it.getBoolean("test") == test }) {
      val audio = context.getSystemService(AudioManager::class.java)
      val volume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
      sessions.add(JSONObject().put("sessionId", UUID.randomUUID().toString()).put("alarm", alarm).put("baseAlarm", JSONObject(base.toString()))
        .put("wakeRetry", wakeRetry).put("action", JSONObject.NULL).put("taskIndex", 0).put("round", 1).put("prompt", JSONObject.NULL)
        .put("kind", JSONObject.NULL).put("test", test).put("muted", false).put("originalVolume", volume)
        .put("guardVolume", VolumePolicy.guardedLevel(volume, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM))))
      store.sessions(sessions)
    }
  }
  private fun retryWake(record: JSONObject) {
    enqueue(record.getJSONObject("alarm"), record.optBoolean("test"), true)
    ring()
  }
  fun wakeEvent(id: String, phase: String) = synchronized(LOCK) {
    when (phase) {
      "reminder" -> wakeChecks.remind(id)
      "dismiss_early" -> wakeChecks.dismissEarly(id)
      "due" -> wakeChecks.due(id)
      "deadline" -> wakeChecks.deadline(id)
      "yes" -> wakeChecks.respond(id, true)
      "no" -> wakeChecks.respond(id, false)
    }
  }
  fun respondWake(id: String, awake: Boolean) = synchronized(LOCK) { wakeChecks.respond(id, awake) }
  fun submitWake(id: String, challengeId: String, answer: String): Boolean = synchronized(LOCK) { wakeChecks.submit(id, challengeId, answer) }

  fun begin(sessionId: String, action: String) = synchronized(LOCK) {
    require(action in listOf("dismiss", "snooze"))
    val sessions = store.sessions(); val session = current(sessions, sessionId)
    if (session.optString("action") == action) return@synchronized
    // Tapping an action after the unanswered timeout still escalates first.
    Escalation.refresh(session, Escalation.clock(context))
    if (action == "snooze") check(snoozesLeft(session.getJSONObject("alarm")) != 0) {
      if (!session.getJSONObject("alarm").optBoolean("allowSnooze", true)) "Snooze is turned off for this alarm" else "No snoozes left — complete the dismiss tasks"
    }
    session.put("action", action).put("muted", false)
    val alarm = session.getJSONObject("alarm")
    val key = if (action == "snooze") "snoozeTasks" else "dismissTasks"
    if (alarm.optBoolean("shuffleTasks")) alarm.put(key, TaskOrder.shuffled(alarm.optJSONArray(key) ?: JSONArray()))
    val tasks = tasks(session)
    if (tasks.length() == 0) finish(sessions)
    else { SessionTasks.start(session, tasks, now()); store.sessions(sessions); ring() }
  }
  private fun current(sessions: List<JSONObject>, id: String): JSONObject {
    val session = sessions.firstOrNull() ?: error("No active alarm")
    check(session.getString("sessionId") == id) { "This alarm session has changed" }
    return session
  }
  private fun tasks(session: JSONObject) = session.getJSONObject("alarm")
    .getJSONArray(if (session.getString("action") == "snooze") "snoozeTasks" else "dismissTasks")

  fun setMuted(sessionId: String, muted: Boolean) = synchronized(LOCK) {
    val sessions = store.sessions(); val session = current(sessions, sessionId)
    check(VolumePolicy.mayMute(session.getJSONObject("alarm").optBoolean("allowMuteWhileSolving"), !session.isNull("action") && tasks(session).length() > 0)) { "Muting is only allowed while solving tasks for this alarm" }
    session.put("muted", muted); store.sessions(sessions); ring()
  }

  fun submit(sessionId: String, challengeId: String, supplied: String): Boolean = synchronized(LOCK) {
    val sessions = store.sessions(); val session = current(sessions, sessionId)
    check(!session.isNull("action")) { "Choose dismiss or snooze first" }
    return@synchronized advanced(sessions, SessionTasks.submit(session, tasks(session), challengeId, supplied, now()))
  }

  /** Completion report from an interactive task (breakout, maze, walk, light). */
  fun complete(sessionId: String, challengeId: String) = synchronized(LOCK) {
    val sessions = store.sessions(); val session = current(sessions, sessionId)
    check(!session.isNull("action")) { "Choose dismiss or snooze first" }
    advanced(sessions, SessionTasks.complete(session, tasks(session), challengeId, now()))
  }

  /** Replaces an unavailable sensor task with its math/text fallback for this ring. */
  fun fallback(sessionId: String, challengeId: String) = synchronized(LOCK) {
    val sessions = store.sessions(); val session = current(sessions, sessionId)
    check(!session.isNull("action")) { "Choose dismiss or snooze first" }
    SessionTasks.fallback(session, tasks(session), challengeId, now())
    store.sessions(sessions); ring()
  }
  fun completeWake(id: String, challengeId: String) = synchronized(LOCK) { wakeChecks.complete(id, challengeId) }
  fun fallbackWake(id: String, challengeId: String) = synchronized(LOCK) { wakeChecks.fallback(id, challengeId) }

  private fun advanced(sessions: MutableList<JSONObject>, result: SessionTasks.Result): Boolean = when (result) {
    SessionTasks.Result.WRONG -> false
    // Ring again so the service sees the new task (a light task pauses the flashlight).
    SessionTasks.Result.NEXT -> { store.sessions(sessions); ring(); true }
    SessionTasks.Result.COMPLETE -> { finish(sessions); true }
  }

  private fun finish(sessions: MutableList<JSONObject>) {
    val session = sessions.first()
    val base = JSONObject((session.optJSONObject("baseAlarm") ?: session.getJSONObject("alarm")).toString())
    if (!session.getBoolean("test")) {
      if (session.getString("action") == "snooze") {
        check(readiness().getBoolean("exactAlarms")) { "Restore Alarms & reminders access before snoozing" }
        base.put("enabled", true).put("snoozed", true).put("wakeRetry", session.optBoolean("wakeRetry"))
          .put("snoozeCount", session.getJSONObject("alarm").optInt("snoozeCount", 0) + 1)
          .put("nextAt", now() + base.getInt("snoozeMinutes") * 60_000L)
        schedule(base); store.put(base)
      } else wakeChecks.schedule(base)
    }
    sessions.removeAt(0); store.sessions(sessions)
    if (sessions.isEmpty()) {
      cancelBackstop(); FallbackAlert.cancel(context)
      context.stopService(Intent(context, RingingService::class.java))
    } else ring()
  }

  fun restore(action: String?) = synchronized(LOCK) {
    if (!readiness().getBoolean("exactAlarms")) return@synchronized
    for (alarm in store.alarms().filter { it.getBoolean("enabled") }) {
      if (!alarm.optBoolean("snoozed") || alarm.optLong("nextAt") <= now()) alarm.put("nextAt", next(alarm))
      schedule(alarm); store.put(alarm)
    }
    wakeChecks.restore()
    // Boot receivers cannot start a media FGS directly; an exact alarm can resume an interrupted ring.
    if (store.sessions().isNotEmpty()) armBackstop(1000)
  }

  /**
   * Re-registers platform timers from the store. Android drops every alarm on force stop without notifying the
   * app, so this runs on each app launch. Unlike [restore], stored times are kept: an occurrence that became due
   * moments ago is scheduled as-is (AlarmManager delivers it immediately) instead of skipping to the next day.
   */
  fun rearm() = synchronized(LOCK) {
    if (!readiness().getBoolean("exactAlarms")) return@synchronized
    for (alarm in store.alarms().filter { it.getBoolean("enabled") }) {
      if (alarm.optLong("nextAt", 0) < now() - REARM_GRACE_MS) alarm.remove("snoozed")
      if (!alarm.has("nextAt") || alarm.getLong("nextAt") < now() - REARM_GRACE_MS) alarm.put("nextAt", next(alarm))
      schedule(alarm); store.put(alarm)
    }
    wakeChecks.restore(fromBoot = false)
  }

  /** Starts (or refreshes) ringing. If Android refuses the service, an alarm-sound notification still alerts. */
  fun ring() {
    armBackstop()
    try { context.startForegroundService(Intent(context, RingingService::class.java)) }
    catch (error: RuntimeException) {
      Log.e("TaskAlarm", "Unable to start ringing service", error)
      FallbackAlert.show(context, store.sessions().firstOrNull()?.getJSONObject("alarm")?.optString("label") ?: "Alarm")
    }
  }

  /** Exact alarm that restarts ringing if the process dies mid-alarm. The ringing service keeps pushing it back. */
  fun armBackstop(delayMs: Long = BACKSTOP_MS) {
    if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) return
    val show = PendingIntent.getActivity(context, 0, launchIntent(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    try { manager.setAlarmClock(AlarmManager.AlarmClockInfo(now() + delayMs, show), backstopIntent()) }
    catch (error: SecurityException) { Log.e("TaskAlarm", "Unable to arm ringing backstop", error) }
  }
  fun cancelBackstop() = manager.cancel(backstopIntent())
  fun backstop() = synchronized(LOCK) { if (store.sessions().isNotEmpty()) ring() }

  companion object {
    val LOCK = Any()
    /** Snoozes still allowed for this occurrence: -1 = unlimited, 0 = none (snooze off or limit reached). */
    fun snoozesLeft(alarm: JSONObject): Int {
      if (!alarm.optBoolean("allowSnooze", true)) return 0
      val limit = alarm.optInt("snoozeLimit", 0)
      return if (limit == 0) -1 else (limit - alarm.optInt("snoozeCount", 0)).coerceAtLeast(0)
    }
    /**
     * Explicit app launch intent. getLaunchIntentForPackage hides non-direct-boot-aware activities before the
     * first unlock, which would break scheduling and ringing notifications on a locked phone after reboot.
     */
    fun launchIntent(context: Context): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
      .setClassName(context, "com.taskalarm.app.MainActivity")
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    const val BACKSTOP_MS = 60_000L
    const val REARM_GRACE_MS = 60_000L
  }
}
