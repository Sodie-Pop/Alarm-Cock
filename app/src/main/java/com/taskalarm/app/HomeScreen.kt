package com.taskalarm.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun HomeScreen(state: JSONObject?, busy: Boolean, snackbar: SnackbarHostState, now: Long,
  onAdd: () -> Unit, onEdit: (AlarmDraft) -> Unit, onToggle: (AlarmDraft, Boolean) -> Unit,
  onDelete: (AlarmDraft) -> Unit, onTest: (AlarmDraft) -> Unit, onAccess: (String) -> Unit, onSettings: () -> Unit = {},
  onSkip: (AlarmDraft, Boolean) -> Unit = { _, _ -> }, onPause: (AlarmDraft, Long) -> Unit = { _, _ -> },
  onDuplicate: (AlarmDraft, Int, Int) -> Unit = { _, _, _ -> }) {
  var pausing by remember { mutableStateOf<AlarmDraft?>(null) }
  var duplicating by remember { mutableStateOf<AlarmDraft?>(null) }
  val array = state?.optJSONArray("alarms")
  val stored = if (array == null) emptyList() else (0 until array.length()).map { array.getJSONObject(it) }
    .sortedBy { it.getString("hour").toInt() * 60 + it.getString("minute").toInt() }
  val checksArray = state?.optJSONArray("wakeChecks")
  val checks = if (checksArray == null) emptyList() else (0 until checksArray.length()).map { checksArray.getJSONObject(it) }
  val is24 = is24Hour()
  Scaffold(
    snackbarHost = { SnackbarHost(snackbar) },
    floatingActionButton = {
      if (stored.isNotEmpty()) ExtendedFloatingActionButton(onClick = { if (!busy) onAdd() },
        icon = { Ico(R.drawable.ic_add) }, text = { Text("New alarm") })
    },
  ) { padding ->
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
      top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 104.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item { HomeHeader(stored, now, is24, accessGroups(state?.optJSONObject("readiness")).attention, onSettings) }
      item { SetupCard(state?.optJSONObject("readiness"), onAccess, onSettings) }
      items(checks, key = { "check-" + it.getString("sessionId") }) { WakeCheckPending(it, is24) }
      if (state != null && stored.isEmpty()) item { EmptyAlarms(onAdd) }
      items(stored, key = { it.getString("id") }) { value ->
        AlarmCard(AlarmDraft.from(value), busy, is24, now, onEdit, onToggle, onDelete, onTest, onSkip, { pausing = it }, { onPause(it, 0) },
          { duplicating = it })
      }
    }
  }
  pausing?.let { alarm -> PauseDialog(onDismiss = { pausing = null }) { until -> pausing = null; onPause(alarm, until) } }
  duplicating?.let { alarm -> DuplicateDialog(alarm, is24, onDismiss = { duplicating = null }) { count, interval -> duplicating = null; onDuplicate(alarm, count, interval) } }
}

@Composable
private fun HomeHeader(alarms: List<JSONObject>, now: Long, is24: Boolean, attention: Int, onSettings: () -> Unit) {
  val next = alarms.filter { it.getBoolean("enabled") && it.has("nextAt") }.minOfOrNull { it.getLong("nextAt") }
  Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Alarms", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
      IconButton(onClick = onSettings) {
        BadgedBox(badge = { if (attention > 0) Badge { Text(attention.toString()) } }) {
          Ico(R.drawable.ic_settings, if (attention > 0) "Settings, $attention need attention" else "Settings")
        }
      }
    }
    if (next != null) {
      val day = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault()).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
      val clock = clockText(next, is24)
      Text("Next alarm in ${durationText(next - now)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
      Text("$day at ${clock.time}${if (clock.period.isEmpty()) "" else " ${clock.period}"}",
        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (alarms.isNotEmpty()) {
      Text("No alarms are on", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun WakeCheckPending(check: JSONObject, is24: Boolean) {
  val ask = clockText(check.getLong("dueAt"), is24)
  val reply = clockText(check.getLong("deadlineAt"), is24)
  Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
    Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Ico(R.drawable.ic_wb_sunny, size = 28.dp)
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Wake-up check · ${check.getString("label")}", style = MaterialTheme.typography.titleSmall)
        Text("Asks at ${ask.time} ${ask.period}. Answer by ${reply.time} ${reply.period} or it rings again.".replace("  ", " "),
          style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

@Composable
private fun EmptyAlarms(onAdd: () -> Unit) {
  Column(Modifier.fillMaxWidth().padding(top = 72.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp)) {
    IconBadge(R.drawable.ic_alarm_clock, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, size = 112.dp)
    Text("No alarms yet", style = MaterialTheme.typography.headlineSmall)
    Text("Set a time and pick the tasks you'll need to finish before the alarm stops — a little challenge between you and the snooze button.",
      style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Button(onClick = onAdd, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
      Ico(R.drawable.ic_add, size = 20.dp); Spacer(Modifier.width(8.dp)); Text("Add an alarm")
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmCard(alarm: AlarmDraft, busy: Boolean, is24: Boolean, now: Long, onEdit: (AlarmDraft) -> Unit,
  onToggle: (AlarmDraft, Boolean) -> Unit, onDelete: (AlarmDraft) -> Unit, onTest: (AlarmDraft) -> Unit,
  onSkip: (AlarmDraft, Boolean) -> Unit, onPause: (AlarmDraft) -> Unit, onResume: (AlarmDraft) -> Unit, onDuplicate: (AlarmDraft) -> Unit) {
  val skipping = alarm.skipDate.isNotEmpty() && alarm.skipDate >= java.time.LocalDate.now().toString()
  val paused = alarm.pauseUntil > now
  val clock = clockText(alarm.hour.toInt(), alarm.minute.toInt(), is24)
  val on = alarm.enabled
  val dim = MaterialTheme.colorScheme.onSurfaceVariant
  var menu by remember { mutableStateOf(false) }
  Card(onClick = { if (!busy) onEdit(alarm) }, shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = if (on) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow)) {
    Column(Modifier.padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.Bottom) {
            Text(clock.time, style = clockStyle(56.sp), color = if (on) MaterialTheme.colorScheme.onSurface else dim,
              modifier = Modifier.alignByBaseline())
            if (clock.period.isNotEmpty()) Text(clock.period, style = MaterialTheme.typography.titleLarge, color = dim,
              modifier = Modifier.padding(start = 6.dp).alignByBaseline())
          }
          Text("${alarm.label} · ${repeatText(alarm.days)}", style = MaterialTheme.typography.bodyLarge, color = dim,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
          if (on && (skipping || paused)) Text(if (paused) "Paused until ${shortDate(alarm.pauseUntil)}"
            else "Skipping ${shortDate(java.time.LocalDate.parse(alarm.skipDate))}", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        }
        Switch(checked = on, enabled = !busy, onCheckedChange = { onToggle(alarm, it) })
        Box {
          IconButton(onClick = { menu = true }, enabled = !busy) { Ico(R.drawable.ic_more_vert, "More options for ${alarm.label}") }
          DropdownMenu(menu, onDismissRequest = { menu = false }) {
            if (on && alarm.days.isNotEmpty()) DropdownMenuItem(text = { Text(if (skipping) "Undo skip" else "Skip next") },
              leadingIcon = { Ico(R.drawable.ic_snooze) }, onClick = { menu = false; onSkip(alarm, !skipping) })
            DropdownMenuItem(text = { Text(if (paused) "Resume now" else "Pause until…") }, leadingIcon = { Ico(R.drawable.ic_timer) },
              onClick = { menu = false; if (paused) onResume(alarm) else onPause(alarm) })
            DropdownMenuItem(text = { Text("Duplicate…") }, leadingIcon = { Ico(R.drawable.ic_add) }, onClick = { menu = false; onDuplicate(alarm) })
            DropdownMenuItem(text = { Text("Test now") }, leadingIcon = { Ico(R.drawable.ic_play_arrow) },
              onClick = { menu = false; onTest(alarm) })
            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Ico(R.drawable.ic_delete) },
              onClick = { menu = false; onDelete(alarm) })
          }
        }
      }
      Spacer(Modifier.height(12.dp))
      FlowRow(Modifier.padding(end = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (alarm.dismissTasks.isEmpty()) InfoPill(R.drawable.ic_check, "No tasks")
        alarm.dismissTasks.forEach { InfoPill(taskIcon(it.kind), taskPill(it)) }
        if (!alarm.allowSnooze) InfoPill(R.drawable.ic_snooze, "No snooze")
        else InfoPill(R.drawable.ic_snooze, "${alarm.snoozeMinutes} min" + (if (alarm.snoozeLimit > 0) " ×${alarm.snoozeLimit}" else "") +
          if (alarm.snoozeTasks.isNotEmpty()) " · ${alarm.snoozeTasks.size} task${if (alarm.snoozeTasks.size > 1) "s" else ""}" else "")
        if (alarm.vibrate) InfoPill(R.drawable.ic_vibration, "Vibrate")
        if (alarm.escalationEnabled) InfoPill(R.drawable.ic_campaign, "Escalates")
        if (alarm.wakeEnabled) InfoPill(R.drawable.ic_wb_sunny, "Wake check")
        if (alarm.flashlightMode != "off") InfoPill(R.drawable.ic_flashlight_on, if (alarm.flashlightMode == "flashing") "Flashing" else "Light")
      }
    }
  }
}

internal fun shortDate(millis: Long): String = shortDate(java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())
internal fun shortDate(date: java.time.LocalDate): String =
  date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))

/** Pick the date the alarm should start ringing again (from local midnight). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PauseDialog(onDismiss: () -> Unit, onPause: (Long) -> Unit) {
  val tomorrow = java.time.LocalDate.now().plusDays(1)
  val state = rememberDatePickerState(initialSelectedDateMillis = tomorrow.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
    selectableDates = object : SelectableDates {
      override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= tomorrow.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    })
  DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
    TextButton(onClick = {
      val picked = java.time.Instant.ofEpochMilli(state.selectedDateMillis ?: return@TextButton).atZone(java.time.ZoneOffset.UTC).toLocalDate()
      onPause(picked.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }) { Text("Pause") }
  }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }) {
    DatePicker(state, title = { Text("Ring again from", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
  }
}

/** Create copies every N minutes after this alarm, previewing their times. */
@Composable
internal fun DuplicateDialog(alarm: AlarmDraft, is24: Boolean, onDismiss: () -> Unit, onCreate: (Int, Int) -> Unit) {
  var count by remember { mutableIntStateOf(5) }
  var interval by remember { mutableIntStateOf(5) }
  val fits = count * interval < 1440
  val start = alarm.hour.toInt() * 60 + alarm.minute.toInt()
  AlertDialog(onDismissRequest = onDismiss, icon = { Ico(R.drawable.ic_add) }, title = { Text("Duplicate ${alarm.label}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Stepper("Copies", count, "$count", count > 1, count < 50, { count-- }, { count++ }, horizontalPadding = 0.dp)
        ChipChoice("Every", listOf(1, 2, 5, 10, 15, 30, 60), interval, ::minutesText, inset = 0.dp) { interval = it }
        if (fits) Text((1..count).take(6).joinToString(", ") { i ->
          val m = (start + i * interval) % 1440; clockText(m / 60, m % 60, is24).let { "${it.time}${if (it.period.isEmpty()) "" else " " + it.period}" }
        } + if (count > 6) " …and ${count - 6} more" else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Notice("Copies must all fall within 24 hours.")
        Text("Copies keep this alarm's tasks and settings, and won't ring before it.", style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    },
    confirmButton = { TextButton(onClick = { onCreate(count, interval) }, enabled = fits) { Text("Create $count") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
