package com.taskalarm.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TaskDraft(
  val id: String = UUID.randomUUID().toString(),
  val kind: String = "math",
  val difficulty: String = "easy",
  val rounds: Int = 1,
  val barcodeMode: String = "any",
  val barcode: String = "",
  /** Breakout: seconds of active play per round. */
  val gameSeconds: Int = 60,
  /** Walking: steps per round. */
  val stepTarget: Int = 20,
  /** Light: brightness to reach and how long to hold it. */
  val lightLux: Int = 200,
  val lightHoldSeconds: Int = 3,
  /** Maze: "tilt" or "touch". */
  val controlMode: String = "tilt",
  /** Walking, light and tilt maze: task offered if the sensor is unavailable ("math" or "text"). */
  val fallbackKind: String = "math",
) {
  fun json() = JSONObject().put("id", id).put("kind", kind).put("difficulty", difficulty).put("rounds", rounds)
    .put("barcodeMode", barcodeMode).put("barcode", barcode).put("gameSeconds", gameSeconds).put("stepTarget", stepTarget)
    .put("lightLux", lightLux).put("lightHoldSeconds", lightHoldSeconds).put("controlMode", controlMode).put("fallbackKind", fallbackKind)
  val example get() = when (kind) {
    "text" -> when (difficulty) { "easy" -> "kefuma"; "medium" -> "K7mP3xRa9q"; else -> "R8!mQ2#vL9@pX4&s" }
    "memory" -> when (difficulty) { "easy" -> "1 → 3 → 2"; "medium" -> "1 → 3 → 2 → 4 → 1"; else -> "1 → 3 → 2 → 4 → 1 → 2 → 3" }
    else -> when (difficulty) { "easy" -> "7 + 5 = ?"; "medium" -> "12 × 7 + 18 = ?"; else -> "(23 × 14) − (12 × 7) = ?" }
  }
  val hasDifficulty get() = kind in listOf("text", "math", "memory", "breakout", "maze")
  /** Kinds that can't run without a sensor, and so need a fallback. */
  val usesSensor get() = kind == "walk" || kind == "light" || (kind == "maze" && controlMode == "tilt")
  companion object {
    val KINDS = listOf("math", "text", "memory", "barcode", "breakout", "maze", "walk", "light")
    fun from(value: JSONObject) = TaskDraft(value.getString("id"), value.getString("kind"), value.optString("difficulty", "easy"),
      value.optInt("rounds", 1), value.optString("barcodeMode", "any"), value.optString("barcode"), value.optInt("gameSeconds", 60),
      value.optInt("stepTarget", 20), value.optInt("lightLux", 200), value.optInt("lightHoldSeconds", 3),
      value.optString("controlMode", "tilt"), value.optString("fallbackKind", "math"))
  }
}

/** A sound reference: `builtin:<id>`, a content/file URI, or an android.resource URI. */
data class SoundRef(val uri: String, val name: String) {
  fun json(): JSONObject = JSONObject().put("uri", uri).put("name", name)
  companion object {
    val BUILTIN = listOf(SoundRef("builtin:beacon", "Beacon"), SoundRef("builtin:pulse", "Pulse"), SoundRef("builtin:chime", "Chime"))
  }
}

data class AlarmDraft(
  val id: String = UUID.randomUUID().toString(),
  val label: String = "Morning alarm",
  val hour: String = "07",
  val minute: String = "00",
  val days: Set<Int> = setOf(1, 2, 3, 4, 5),
  val enabled: Boolean = true,
  val snoozeMinutes: Int = 5,
  val dismissTasks: List<TaskDraft> = listOf(TaskDraft()),
  val snoozeTasks: List<TaskDraft> = emptyList(),
  val soundUri: String = "",
  val soundName: String = "Default phone alarm",
  val normalize: Boolean = false,
  val playbackUri: String = "",
  val protectVolume: Boolean = false,
  val allowMuteWhileSolving: Boolean = false,
  val wakeEnabled: Boolean = false,
  val wakeDelayMinutes: Int = 5,
  val wakeResponseMinutes: Int = 5,
  val wakeMode: String = "yes_no",
  val wakeTasks: List<TaskDraft> = listOf(TaskDraft()),
  val wakeReuseDismissTasks: Boolean = true,
  val wakeRetryTasks: List<TaskDraft> = listOf(TaskDraft()),
  val flashlightMode: String = "off",
  val allowSnooze: Boolean = true,
  /** 0 = unlimited. */
  val snoozeLimit: Int = 0,
  val harderAfterSnooze: Boolean = false,
  val shuffleTasks: Boolean = false,
  val vibrate: Boolean = false,
  val movingDismiss: Boolean = false,
  val wakeRandomDelay: Boolean = false,
  val wakeDelayMinMinutes: Int = 3,
  val wakeDelayMaxMinutes: Int = 7,
  val escalationEnabled: Boolean = false,
  val escalationAfterMinutes: Int = 2,
  val escalationTones: List<SoundRef> = SoundRef.BUILTIN,
  /** Schedule exceptions: no rings before [pauseUntil]; [skipDate] (local ISO date) is skipped once. */
  val pauseUntil: Long = 0,
  val skipDate: String = "",
  /** Internal boundary for duplicated alarms: never ring before the source's first occurrence. */
  val notBefore: Long = 0,
) {
  fun json() = JSONObject().put("id", id).put("label", label).put("hour", hour).put("minute", minute)
    .put("days", JSONArray(days.sorted())).put("enabled", enabled).put("snoozeMinutes", snoozeMinutes)
    .put("dismissTasks", JSONArray(dismissTasks.map { it.json() })).put("snoozeTasks", JSONArray(snoozeTasks.map { it.json() }))
    .put("soundUri", soundUri).put("soundName", soundName).put("normalize", normalize).put("playbackUri", playbackUri)
    .put("protectVolume", protectVolume).put("allowMuteWhileSolving", allowMuteWhileSolving)
    .put("wakeEnabled", wakeEnabled).put("wakeDelayMinutes", wakeDelayMinutes).put("wakeResponseMinutes", wakeResponseMinutes)
    .put("wakeMode", wakeMode).put("wakeTasks", JSONArray(wakeTasks.map { it.json() }))
    .put("wakeReuseDismissTasks", wakeReuseDismissTasks).put("wakeRetryTasks", JSONArray(wakeRetryTasks.map { it.json() }))
    .put("flashlightMode", flashlightMode)
    .put("allowSnooze", allowSnooze).put("snoozeLimit", snoozeLimit).put("harderAfterSnooze", harderAfterSnooze)
    .put("shuffleTasks", shuffleTasks).put("vibrate", vibrate).put("movingDismiss", movingDismiss)
    .put("escalationEnabled", escalationEnabled).put("escalationAfterMinutes", escalationAfterMinutes)
    .put("escalationTones", JSONArray(escalationTones.map { it.json() }))
    .put("pauseUntil", pauseUntil).put("skipDate", skipDate).put("notBefore", notBefore)
    .put("wakeRandomDelay", wakeRandomDelay).put("wakeDelayMinMinutes", wakeDelayMinMinutes).put("wakeDelayMaxMinutes", wakeDelayMaxMinutes)

  fun validate() {
    require(hour.toIntOrNull() in 0..23 && minute.toIntOrNull() in 0..59) { "Enter a valid 24-hour time." }
    require(snoozeMinutes in 1..60) { "Choose a snooze length from 1 to 60 minutes." }
    require(flashlightMode in listOf("off", "steady", "flashing")) { "Choose a valid flashlight mode." }
    require(snoozeLimit in SNOOZE_LIMITS) { "Choose a valid snooze limit." }
    if (escalationEnabled) {
      require(escalationAfterMinutes in 1..120) { "Escalation must start after 1–120 minutes." }
      require(escalationTones.size in 1..12) { "Add 1–12 sounds to the escalation playlist." }
    }
    if (wakeEnabled) {
      require(wakeDelayMinutes in 1..1440 && wakeResponseMinutes in 1..1440) { "Wake-up check times must be 1–1440 minutes." }
      require(!wakeRandomDelay || (wakeDelayMinMinutes in 1..1440 && wakeDelayMaxMinutes in wakeDelayMinMinutes..1440)) { "Choose a random delay range from 1 to 1440 minutes." }
      require(wakeMode in listOf("yes_no", "tasks"))
      require(wakeMode != "tasks" || wakeTasks.isNotEmpty()) { "Choose at least one task to confirm you’re awake." }
    }
    val configured = dismissTasks + (if (allowSnooze) snoozeTasks else emptyList()) + if (wakeEnabled) (if (wakeMode == "tasks") wakeTasks else emptyList()) + (if (!wakeReuseDismissTasks) wakeRetryTasks else emptyList()) else emptyList()
    for (task in configured) {
      require(task.kind != "barcode" || task.barcodeMode != "specific" || task.barcode.isNotEmpty()) { "Scan the target barcode for each specific-barcode task." }
    }
  }
  fun tasks(action: String) = when (action) { "dismiss" -> dismissTasks; "snooze" -> snoozeTasks; "wake" -> wakeTasks; "retry" -> wakeRetryTasks; else -> error("Unknown task list") }
  fun withTasks(action: String, tasks: List<TaskDraft>) = when (action) {
    "dismiss" -> copy(dismissTasks = tasks); "snooze" -> copy(snoozeTasks = tasks)
    "wake" -> copy(wakeTasks = tasks); "retry" -> copy(wakeRetryTasks = tasks); else -> error("Unknown task list")
  }
  companion object {
    val SNOOZE_LIMITS = listOf(0, 1, 2, 3, 5, 10)
    fun from(value: JSONObject): AlarmDraft {
      fun tasks(key: String, fallback: List<TaskDraft> = emptyList()): List<TaskDraft> {
        val array = value.optJSONArray(key) ?: return fallback
        return (0 until array.length()).map { TaskDraft.from(array.getJSONObject(it)) }
      }
      val days = value.getJSONArray("days")
      return AlarmDraft(value.getString("id"), value.getString("label"), value.getString("hour"),
        value.getString("minute"), (0 until days.length()).map { days.getInt(it) }.toSet(),
        value.getBoolean("enabled"), value.getInt("snoozeMinutes"), tasks("dismissTasks"), tasks("snoozeTasks"),
        value.optString("soundUri"), value.optString("soundName", "Default phone alarm"), value.optBoolean("normalize"), value.optString("playbackUri"),
        value.optBoolean("protectVolume"), value.optBoolean("allowMuteWhileSolving"), value.optBoolean("wakeEnabled"),
        value.optInt("wakeDelayMinutes", 5), value.optInt("wakeResponseMinutes", 5), value.optString("wakeMode", "yes_no"),
        tasks("wakeTasks", listOf(TaskDraft())), value.optBoolean("wakeReuseDismissTasks", true), tasks("wakeRetryTasks", listOf(TaskDraft())),
        value.optString("flashlightMode", "off"),
        allowSnooze = value.optBoolean("allowSnooze", true), snoozeLimit = value.optInt("snoozeLimit", 0),
        harderAfterSnooze = value.optBoolean("harderAfterSnooze"), shuffleTasks = value.optBoolean("shuffleTasks"),
        vibrate = value.optBoolean("vibrate"), movingDismiss = value.optBoolean("movingDismiss"),
        wakeRandomDelay = value.optBoolean("wakeRandomDelay"), wakeDelayMinMinutes = value.optInt("wakeDelayMinMinutes", 3),
        wakeDelayMaxMinutes = value.optInt("wakeDelayMaxMinutes", 7),
        escalationEnabled = value.optBoolean("escalationEnabled"), escalationAfterMinutes = value.optInt("escalationAfterMinutes", 2),
        escalationTones = value.optJSONArray("escalationTones")?.let { array ->
          (0 until array.length()).map { array.getJSONObject(it) }.map { SoundRef(it.getString("uri"), it.optString("name", "Sound")) }
        } ?: SoundRef.BUILTIN,
        pauseUntil = value.optLong("pauseUntil", 0), skipDate = value.optString("skipDate"), notBefore = value.optLong("notBefore", 0))
    }
  }
}
