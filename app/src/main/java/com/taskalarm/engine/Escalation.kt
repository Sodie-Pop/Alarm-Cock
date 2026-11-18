package com.taskalarm.engine

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Maximum-volume escalation: when an alarm goes unanswered (or from the third ring after two snoozes) the volume is
 * forced to maximum and the sound becomes a playlist that changes every [TONE_MS] and loops.
 *
 * Times are stamped with wall clock, elapsedRealtime and boot count so ages survive clock changes and process restarts.
 */
internal object Escalation {
  const val TONE_MS = 45_000L
  const val SNOOZES_TO_ESCALATE = 2

  data class Clock(val wall: Long, val elapsed: Long, val boot: Int)

  fun clock(context: Context) = Clock(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
    Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1))

  fun stamp(record: JSONObject, prefix: String, clock: Clock) {
    record.put("${prefix}Wall", clock.wall).put("${prefix}Elapsed", clock.elapsed).put("${prefix}Boot", clock.boot)
  }

  fun age(record: JSONObject, prefix: String, clock: Clock): Long? {
    if (!record.has("${prefix}Wall")) return null
    val elapsed = record.optLong("${prefix}Elapsed", -1)
    return if (record.optInt("${prefix}Boot", Int.MIN_VALUE) == clock.boot && elapsed in 0..clock.elapsed) clock.elapsed - elapsed
      else (clock.wall - record.getLong("${prefix}Wall")).coerceAtLeast(0)
  }

  /** Marks [session] escalated when due; returns true if it changed (caller persists). */
  fun refresh(session: JSONObject, clock: Clock): Boolean {
    val alarm = session.getJSONObject("alarm")
    if (!alarm.optBoolean("escalationEnabled") || session.optBoolean("escalated")) return false
    val waited = age(session, "ringStarted", clock)
    val reason = when {
      alarm.optBoolean("escalationLatched") -> "continued"
      alarm.optInt("snoozeCount", 0) >= SNOOZES_TO_ESCALATE -> "snoozes"
      session.isNull("action") && waited != null && waited >= alarm.optInt("escalationAfterMinutes", 2) * 60_000L -> "unanswered"
      else -> return false
    }
    stamp(session, "escalated", clock)
    session.put("escalated", true).put("escalationReason", reason)
    // Carries into snoozed rings and wake-up retries; cleared at the next regular occurrence.
    alarm.put("escalationLatched", true)
    session.optJSONObject("baseAlarm")?.put("escalationLatched", true)
    return true
  }

  fun slot(session: JSONObject, clock: Clock) = (age(session, "escalated", clock) ?: 0) / TONE_MS
  fun untilNextTone(session: JSONObject, clock: Clock) = TONE_MS - (age(session, "escalated", clock) ?: 0) % TONE_MS

  fun tones(alarm: JSONObject): List<Pair<String, String>> {
    val array = alarm.optJSONArray("escalationTones") ?: return BuiltinSounds.defaultPlaylist()
    return (0 until array.length()).map { array.getJSONObject(it) }.map { it.getString("uri") to it.optString("name", "Sound") }
      .ifEmpty { BuiltinSounds.defaultPlaylist() }
  }

  fun validate(alarm: JSONObject) {
    if (!alarm.optBoolean("escalationEnabled")) return
    require(alarm.optInt("escalationAfterMinutes", 2) in 1..120) { "Escalation must start after 1–120 minutes" }
    val tones = alarm.optJSONArray("escalationTones") ?: JSONArray()
    require(tones.length() in 1..12) { "Choose 1–12 escalation sounds" }
    for (i in 0 until tones.length()) {
      val tone = tones.getJSONObject(i)
      val uri = tone.getString("uri")
      require(uri.length <= 4096 && (BuiltinSounds.isBuiltin(uri) || uri.startsWith("content:") || uri.startsWith("file:") ||
        uri.startsWith("android.resource:"))) { "Unsupported escalation sound" }
      require(tone.optString("name").length <= 120) { "Sound name is too long" }
    }
  }
}
