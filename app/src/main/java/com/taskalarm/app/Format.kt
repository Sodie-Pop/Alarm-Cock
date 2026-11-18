package com.taskalarm.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.taskalarm.engine.Timing
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

internal data class ClockText(val time: String, val period: String)

internal fun clockText(hour: Int, minute: Int, is24: Boolean, locale: Locale = Locale.getDefault()): ClockText {
  val time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
  return if (is24) ClockText(time.format(DateTimeFormatter.ofPattern("HH:mm", locale)), "")
  else ClockText(time.format(DateTimeFormatter.ofPattern("h:mm", locale)), time.format(DateTimeFormatter.ofPattern("a", locale)))
}

internal fun clockText(millis: Long, is24: Boolean): ClockText {
  val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
  return clockText(time.hour, time.minute, is24)
}

@Composable
internal fun is24Hour(): Boolean = android.text.format.DateFormat.is24HourFormat(LocalContext.current)

/** "8 hr 12 min", "2 days 3 hr", "less than a minute". */
internal fun durationText(millis: Long): String {
  val minutes = ceil(millis / 60_000.0).toLong().coerceAtLeast(0)
  if (minutes < 1) return "less than a minute"
  val days = minutes / 1440; val hours = minutes % 1440 / 60; val mins = minutes % 60
  return listOfNotNull(
    days.takeIf { it > 0 }?.let { if (it == 1L) "1 day" else "$it days" },
    hours.takeIf { it > 0 }?.let { "$it hr" },
    mins.takeIf { it > 0 && days == 0L }?.let { "$it min" },
  ).joinToString(" ")
}

internal fun repeatText(days: Set<Int>): String = when {
  days.isEmpty() -> "Once"
  days.size == 7 -> "Every day"
  days == (1..5).toSet() -> "Weekdays"
  days == setOf(6, 7) -> "Weekends"
  else -> days.sorted().joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

internal fun dayLetter(day: Int): String = DayOfWeek.of(day).getDisplayName(TextStyle.NARROW, Locale.getDefault())
internal fun dayName(day: Int): String = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault())

/** Next time this draft would ring if saved now, or null while the time is incomplete. */
internal fun nextRing(alarm: AlarmDraft, now: Long = System.currentTimeMillis()): Long? {
  val hour = alarm.hour.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
  val minute = alarm.minute.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
  return Timing.next(hour, minute, alarm.days, ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault()),
    pauseUntil = maxOf(alarm.pauseUntil, alarm.notBefore), skipDate = alarm.skipDate)
}

internal fun taskIcon(kind: String) = when (kind) {
  "text" -> R.drawable.ic_keyboard
  "math" -> R.drawable.ic_calculate
  "memory" -> R.drawable.ic_grid_view
  "breakout" -> R.drawable.ic_sports_esports
  "maze" -> R.drawable.ic_route
  "walk" -> R.drawable.ic_directions_walk
  "light" -> R.drawable.ic_lightbulb
  else -> R.drawable.ic_qr_code_scanner
}

internal fun taskTitle(kind: String) = when (kind) {
  "text" -> "Type text"
  "math" -> "Solve math"
  "memory" -> "Memory tiles"
  "breakout" -> "Breakout"
  "maze" -> "Ball maze"
  "walk" -> "Take a walk"
  "light" -> "Find the light"
  else -> "Scan a barcode"
}

internal fun taskBlurb(kind: String) = when (kind) {
  "text" -> "Copy a random string exactly. Wakes up your eyes."
  "math" -> "Answer arithmetic problems. Wakes up your brain."
  "memory" -> "Watch tiles light up, then repeat the order."
  "breakout" -> "Keep a brick-breaker game going for a set time."
  "maze" -> "Roll a ball through checkpoints by tilting the phone."
  "walk" -> "Walk a number of steps. Gets you moving."
  "light" -> "Turn on the lights or open the curtains."
  else -> "Scan a product barcode or QR code. Gets you out of bed."
}

internal fun fallbackName(kind: String) = if (kind == "text") "typing text" else "math"
internal fun secondsText(seconds: Int) = if (seconds < 60) "$seconds s" else if (seconds % 60 == 0) "${seconds / 60} min" else "${seconds / 60} min ${seconds % 60} s"

internal fun roundsText(rounds: Int) = if (rounds == 1) "1 round" else "$rounds rounds"

internal fun taskDetail(task: TaskDraft): String = when {
  task.kind == "breakout" -> "${task.difficulty.replaceFirstChar(Char::uppercase)} · ${secondsText(task.gameSeconds)} · ${roundsText(task.rounds)}"
  task.kind == "maze" -> "${task.difficulty.replaceFirstChar(Char::uppercase)} · ${if (task.controlMode == "tilt") "tilt" else "touch"} · ${roundsText(task.rounds)}"
  task.kind == "walk" -> "${task.stepTarget} steps · ${roundsText(task.rounds)}"
  task.kind == "light" -> "${task.lightLux} lux for ${task.lightHoldSeconds} s · ${roundsText(task.rounds)}"
  task.kind != "barcode" -> "${task.difficulty.replaceFirstChar(Char::uppercase)} · ${roundsText(task.rounds)}"
  task.barcodeMode == "specific" -> if (task.barcode.isEmpty()) "Specific barcode · not saved yet" else "Specific barcode · ${roundsText(task.rounds)}"
  else -> "Any barcode · ${roundsText(task.rounds)}"
}

internal fun TaskDraft.needsBarcode() = kind == "barcode" && barcodeMode == "specific" && barcode.isEmpty()

/** Short "Math ×3" style label for summary pills. */
internal fun taskPill(task: TaskDraft): String {
  val name = when (task.kind) { "text" -> "Text"; "math" -> "Math"; "memory" -> "Memory"; "breakout" -> "Breakout"; "maze" -> "Maze"
    "walk" -> "${task.stepTarget} steps"; "light" -> "Light"; else -> "Barcode" }
  return if (task.rounds > 1) "$name ×${task.rounds}" else name
}
