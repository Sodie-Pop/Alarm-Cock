package com.taskalarm.app

import org.json.JSONArray
import java.util.UUID

/** A named set of tasks and settings that can be applied to any alarm. Starter routines are built in and read-only. */
internal data class Routine(val id: String, val name: String, val alarm: AlarmDraft, val starter: Boolean = false)

internal fun routinesFrom(array: JSONArray?): List<Routine> = if (array == null) emptyList() else (0 until array.length())
  .map { array.getJSONObject(it) }.filter { it.optInt("version", 1) == 1 }
  .map { Routine(it.getString("id"), it.getString("name"), AlarmDraft.from(it.getJSONObject("alarm"))) }

internal val StarterRoutines = listOf(
  Routine("starter-quick", "Quick start", AlarmDraft(dismissTasks = listOf(TaskDraft(kind = "math"), TaskDraft(kind = "memory"))), starter = true),
  Routine("starter-moving", "Get moving", AlarmDraft(dismissTasks = listOf(TaskDraft(kind = "math"), TaskDraft(kind = "walk", stepTarget = 20)),
    wakeEnabled = true), starter = true),
  Routine("starter-strong", "Strong wake-up", AlarmDraft(
    dismissTasks = listOf(TaskDraft(kind = "maze", difficulty = "medium"), TaskDraft(kind = "barcode", barcodeMode = "specific")),
    wakeEnabled = true, wakeMode = "tasks", wakeTasks = listOf(TaskDraft(kind = "memory")), harderAfterSnooze = true, snoozeLimit = 2,
    escalationEnabled = true), starter = true),
  Routine("starter-lights", "Lights on", AlarmDraft(dismissTasks = listOf(TaskDraft(kind = "light"), TaskDraft(kind = "text")), wakeEnabled = true),
    starter = true),
)

/** Everything from the routine except the target's identity, schedule and on/off state; task IDs are fresh. */
internal fun Routine.applyTo(target: AlarmDraft): AlarmDraft {
  fun fresh(tasks: List<TaskDraft>) = tasks.map { it.copy(id = UUID.randomUUID().toString()) }
  return alarm.copy(id = target.id, label = target.label, hour = target.hour, minute = target.minute, days = target.days,
    enabled = target.enabled, pauseUntil = target.pauseUntil, skipDate = target.skipDate, notBefore = target.notBefore,
    dismissTasks = fresh(alarm.dismissTasks), snoozeTasks = fresh(alarm.snoozeTasks), wakeTasks = fresh(alarm.wakeTasks),
    wakeRetryTasks = fresh(alarm.wakeRetryTasks))
}

internal fun Routine.summary(): String = (alarm.dismissTasks.map { taskPill(it) }.ifEmpty { listOf("No tasks") } +
  listOfNotNull(if (alarm.wakeEnabled) "wake check" else null, if (alarm.escalationEnabled) "escalation" else null)).joinToString(" · ")
