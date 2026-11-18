package com.taskalarm.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorScreen(alarm: AlarmDraft, isNew: Boolean, readiness: JSONObject?, busy: Boolean, now: Long,
  onChange: (AlarmDraft) -> Unit, onBack: () -> Unit, onSave: () -> Unit, onTest: () -> Unit, onTestWake: () -> Unit,
  onAccess: (String) -> Unit, onSound: (custom: Boolean, playlist: Boolean) -> Unit, onEnroll: (String, String) -> Unit,
  onTestEscalation: () -> Unit = {}, routines: List<Routine> = emptyList(), onSaveRoutine: (String) -> Unit = {},
  onDeleteRoutine: (String) -> Unit = {}) {
  var routineSheet by rememberSaveable { mutableStateOf(false) }
  // "action|taskId" so the sheet survives a trip to the barcode scanner.
  var editing by rememberSaveable { mutableStateOf<String?>(null) }
  var adding by rememberSaveable { mutableStateOf<String?>(null) }
  var soundSheet by rememberSaveable { mutableStateOf(false) }
  var timeDialog by rememberSaveable { mutableStateOf(false) }
  val is24 = is24Hour()
  fun update(change: (AlarmDraft) -> AlarmDraft) { if (!busy) onChange(change(alarm)) }

  Scaffold(
    topBar = {
      TopAppBar(title = { Text(if (isNew) "New alarm" else "Edit alarm") },
        navigationIcon = { IconButton(onClick = onBack, enabled = !busy) { Ico(R.drawable.ic_arrow_back, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
    },
    bottomBar = { EditorActions(busy, onTest, onSave) },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      SetupCard(readiness, onAccess)
      TimeHero(alarm, is24, now) { timeDialog = true }
      DayPicker(alarm.days) { days -> update { it.copy(days = days) } }
      OutlinedTextField(alarm.label, { value -> update { it.copy(label = value.take(120)) } }, Modifier.fillMaxWidth(),
        label = { Text("Alarm name") }, leadingIcon = { Ico(R.drawable.ic_label) }, singleLine = true,
        shape = MaterialTheme.shapes.medium)
      SettingsCard {
        NavRow("Routines", "Apply a saved set of tasks and settings, or save this one", R.drawable.ic_grid_view, !busy) { routineSheet = true }
      }

      SectionHeader("To turn it off", "Finish these, in order, to dismiss the alarm.")
      SettingsCard {
        TaskRows(alarm.dismissTasks, "No tasks — one tap turns the alarm off.", busy, { editing = "dismiss|$it" }, { adding = "dismiss" })
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        SwitchRow("Shuffle task order", "Mixes up the order each time. Every task still counts.", alarm.shuffleTasks, !busy,
          R.drawable.ic_shuffle) { value -> update { it.copy(shuffleTasks = value) } }
      }

      SectionHeader("To snooze", "Snoozing restarts the alarm later; it never skips the turn-off tasks.")
      SettingsCard {
        SwitchRow("Allow snooze", if (alarm.allowSnooze) null else "The ringing screen won't offer Snooze.", alarm.allowSnooze, !busy,
          R.drawable.ic_snooze) { value -> update { it.copy(allowSnooze = value) } }
        if (alarm.allowSnooze) {
          ChipChoice("Snooze for", listOf(1, 5, 10, 15, 30, 60), alarm.snoozeMinutes, ::minutesText, !busy) { minutes -> update { it.copy(snoozeMinutes = minutes) } }
          ChipChoice("Snooze limit", AlarmDraft.SNOOZE_LIMITS, alarm.snoozeLimit, { if (it == 0) "Unlimited" else "$it" }, !busy) { limit ->
            update { it.copy(snoozeLimit = limit) }
          }
          SwitchRow("Harder after each snooze", "Raises task difficulty one level per snooze, up to Hard.", alarm.harderAfterSnooze, !busy) { value ->
            update { it.copy(harderAfterSnooze = value) }
          }
          HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
          TaskRows(alarm.snoozeTasks, "No tasks — one tap snoozes.", busy, { editing = "snooze|$it" }, { adding = "snooze" })
        }
      }

      SectionHeader("Sound & alerts")
      SettingsCard {
        NavRow("Sound", alarm.soundName + if (alarm.normalize && alarm.soundUri.isNotEmpty()) " · boosted" else "", R.drawable.ic_music_note, !busy) { soundSheet = true }
        SwitchRow("Vibrate", "Keeps vibrating while it rings, even when muted.", alarm.vibrate, !busy,
          R.drawable.ic_vibration) { value -> update { it.copy(vibrate = value) } }
        FlashlightRow(alarm, readiness, busy, onAccess) { mode -> update { it.copy(flashlightMode = mode) } }
        SwitchRow("Protect alarm volume", "Stops the volume being turned down while it rings.", alarm.protectVolume, !busy,
          R.drawable.ic_volume_up) { value -> update { it.copy(protectVolume = value) } }
        SwitchRow("Mute while solving", "Shows a mute button during tasks. Leaving the app unmutes.", alarm.allowMuteWhileSolving, !busy,
          R.drawable.ic_volume_off) { value -> update { it.copy(allowMuteWhileSolving = value) } }
        SwitchRow("Moving Dismiss button", "Dismiss jumps somewhere new every 5 seconds; Snooze shrinks into the corner.", alarm.movingDismiss, !busy,
          R.drawable.ic_open_with) { value -> update { it.copy(movingDismiss = value) } }
      }

      SectionHeader("Escalation")
      SettingsCard { EscalationSettings(alarm, busy, ::update, { custom -> onSound(custom, true) }, onTestEscalation) }

      SectionHeader("Wake-up check")
      SettingsCard { WakeSettings(alarm, busy, ::update, { editing = it }, { adding = it }, onTestWake) }
      Spacer(Modifier.height(16.dp))
    }
  }

  editing?.split("|")?.let { (action, id) ->
    val task = alarm.tasks(action).find { it.id == id }
    if (task == null) { editing = null; return@let }
    ModalBottomSheet(onDismissRequest = { editing = null }) {
      TaskSheet(task,
        onChange = { changed -> update { it.withTasks(action, it.tasks(action).map { t -> if (t.id == id) changed else t }) } },
        onRemove = { editing = null; update { it.withTasks(action, it.tasks(action).filterNot { t -> t.id == id }) } },
        onEnroll = { onEnroll(action, id) }, onDone = { editing = null })
    }
  }
  adding?.let { action ->
    ModalBottomSheet(onDismissRequest = { adding = null }) {
      AddTaskSheet { kind ->
        val task = TaskDraft(kind = kind)
        adding = null
        update { it.withTasks(action, it.tasks(action) + task) }
        editing = "$action|${task.id}"
      }
    }
  }
  if (soundSheet) ModalBottomSheet(onDismissRequest = { soundSheet = false }) {
    SoundSheet(alarm, onPick = { custom -> soundSheet = false; onSound(custom, false) },
      onBuiltin = { sound -> soundSheet = false; update { it.copy(soundUri = sound.uri, soundName = sound.name, normalize = false, playbackUri = "") } },
      onDefault = { update { it.copy(soundUri = "", soundName = "Default phone alarm", normalize = false, playbackUri = "") } },
      onNormalize = { value -> update { it.copy(normalize = value) } })
  }
  if (routineSheet) ModalBottomSheet(onDismissRequest = { routineSheet = false }) {
    RoutineSheet(routines, onApply = { routine -> routineSheet = false; update { routine.applyTo(it) } },
      onDelete = onDeleteRoutine, onSave = onSaveRoutine)
  }
  if (timeDialog) TimeDialog(alarm.hour.toIntOrNull() ?: 7, alarm.minute.toIntOrNull() ?: 0, is24, onDismiss = { timeDialog = false }) { hour, minute ->
    timeDialog = false
    update { it.copy(hour = hour.toString().padStart(2, '0'), minute = minute.toString().padStart(2, '0')) }
  }
}

@Composable
private fun TimeHero(alarm: AlarmDraft, is24: Boolean, now: Long, onClick: () -> Unit) {
  val clock = clockText(alarm.hour.toIntOrNull() ?: 0, alarm.minute.toIntOrNull() ?: 0, is24)
  val next = nextRing(alarm, now)
  Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClickLabel = "Change time", onClick = onClick)
    .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.Bottom) {
      Text(clock.time, style = clockStyle(88.sp), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.alignByBaseline())
      if (clock.period.isNotEmpty()) Text(clock.period, style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).alignByBaseline())
    }
    if (next != null) Text("Rings in ${durationText(next - now)}", style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.primary)
    if (alarm.pauseUntil > now) Text("Paused until ${shortDate(alarm.pauseUntil)} · resume from the alarm's menu",
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    else if (alarm.skipDate.isNotEmpty() && alarm.skipDate >= java.time.LocalDate.now().toString())
      Text("Skipping ${shortDate(java.time.LocalDate.parse(alarm.skipDate))}", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun DayPicker(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
      (1..7).forEach { day ->
        val selected = day in days
        Surface(shape = CircleShape, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(44.dp).semantics { contentDescription = dayName(day) }
            .toggleable(selected, role = Role.Checkbox) { onChange(if (it) days + day else days - day) }) {
          Box(contentAlignment = Alignment.Center) { Text(dayLetter(day), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
        }
      }
    }
    Text(if (days.isEmpty()) "Rings once" else "Repeats · ${repeatText(days)}", style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf("Weekdays" to (1..5).toSet(), "Weekends" to setOf(6, 7), "Every day" to (1..7).toSet(), "Once" to emptySet()).forEach { (label, set) ->
        SuggestionChip(onClick = { onChange(set) }, label = { Text(label) })
      }
    }
  }
}

@Composable
private fun TaskRows(tasks: List<TaskDraft>, emptyText: String, busy: Boolean, onEdit: (String) -> Unit, onAdd: () -> Unit) {
  if (tasks.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
  tasks.forEachIndexed { index, task ->
    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { onEdit(task.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      IconBadge(taskIcon(task.kind))
      Column(Modifier.weight(1f)) {
        Text("${index + 1}. ${taskTitle(task.kind)}", style = MaterialTheme.typography.bodyLarge)
        Text(taskDetail(task), style = MaterialTheme.typography.bodyMedium,
          color = if (task.needsBarcode()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Ico(R.drawable.ic_edit, "Edit task", tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp)
    }
  }
  if (tasks.size < 10) TextButton(onClick = onAdd, enabled = !busy, modifier = Modifier.padding(horizontal = 8.dp)) {
    Ico(R.drawable.ic_add, size = 20.dp); Spacer(Modifier.width(8.dp)); Text("Add task")
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlashlightRow(alarm: AlarmDraft, readiness: JSONObject?, busy: Boolean, onAccess: (String) -> Unit, onMode: (String) -> Unit) {
  val available = readiness?.optBoolean("flashlightAvailable", true) != false
  Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      Ico(R.drawable.ic_flashlight_on, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      Text("Flashlight", style = MaterialTheme.typography.bodyLarge)
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
      val modes = listOf("off" to "Off", "steady" to "Steady", "flashing" to "Flashing")
      modes.forEachIndexed { index, (mode, label) ->
        SegmentedButton(selected = alarm.flashlightMode == mode, onClick = { onMode(mode) },
          shape = SegmentedButtonDefaults.itemShape(index, modes.size), enabled = !busy && (mode == "off" || available)) { Text(label) }
      }
    }
    if (!available) Notice("This phone has no flashlight.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (alarm.flashlightMode != "off" && readiness?.optBoolean("cameraPermission") != true) {
      Notice("Android needs camera access to control the flashlight.")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { onAccess("camera") }, enabled = !busy) { Text("Allow camera") }
        TextButton(onClick = { onAccess("appSettings") }, enabled = !busy) { Text("App settings") }
      }
    }
  }
}

@Composable
private fun WakeSettings(alarm: AlarmDraft, busy: Boolean, update: ((AlarmDraft) -> AlarmDraft) -> Unit,
  onEdit: (String) -> Unit, onAdd: (String) -> Unit, onTestWake: () -> Unit) {
  SwitchRow("Make sure I'm up", "After you dismiss, check in later. No answer and it rings again.", alarm.wakeEnabled, !busy,
    R.drawable.ic_wb_sunny) { value -> update { it.copy(wakeEnabled = value) } }
  if (!alarm.wakeEnabled) return
  HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
  SwitchRow("Ask at a random time", "Picks a moment within a range so you can't plan around it.", alarm.wakeRandomDelay, !busy) { value ->
    update { it.copy(wakeRandomDelay = value) }
  }
  if (alarm.wakeRandomDelay) {
    val min = alarm.wakeDelayMinMinutes; val max = alarm.wakeDelayMaxMinutes
    Stepper("Earliest", min, minutesText(min), min > 1, min < max,
      { update { it.copy(wakeDelayMinMinutes = nextMinutes(min, false)) } },
      { update { it.copy(wakeDelayMinMinutes = nextMinutes(min, true).coerceAtMost(max)) } })
    Stepper("Latest", max, minutesText(max), max > min, max < 1440,
      { update { it.copy(wakeDelayMaxMinutes = nextMinutes(max, false).coerceAtLeast(min)) } },
      { update { it.copy(wakeDelayMaxMinutes = nextMinutes(max, true)) } })
  } else Stepper("Ask after", alarm.wakeDelayMinutes, minutesText(alarm.wakeDelayMinutes), alarm.wakeDelayMinutes > 1, alarm.wakeDelayMinutes < 1440,
    { update { it.copy(wakeDelayMinutes = nextMinutes(it.wakeDelayMinutes, false)) } },
    { update { it.copy(wakeDelayMinutes = nextMinutes(it.wakeDelayMinutes, true)) } })
  Stepper("Time to answer", alarm.wakeResponseMinutes, minutesText(alarm.wakeResponseMinutes), alarm.wakeResponseMinutes > 1, alarm.wakeResponseMinutes < 1440,
    { update { it.copy(wakeResponseMinutes = nextMinutes(it.wakeResponseMinutes, false)) } },
    { update { it.copy(wakeResponseMinutes = nextMinutes(it.wakeResponseMinutes, true)) } })
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Confirm by", style = MaterialTheme.typography.bodyLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
      SegmentedButton(alarm.wakeMode == "yes_no", { update { it.copy(wakeMode = "yes_no") } }, SegmentedButtonDefaults.itemShape(0, 2), enabled = !busy) { Text("Tapping Yes") }
      SegmentedButton(alarm.wakeMode == "tasks", { update { it.copy(wakeMode = "tasks") } }, SegmentedButtonDefaults.itemShape(1, 2), enabled = !busy) { Text("Doing tasks") }
    }
  }
  if (alarm.wakeMode == "tasks") TaskRows(alarm.wakeTasks, "Add at least one task.", busy, { onEdit("wake|$it") }, { onAdd("wake") })
  SwitchRow("Same tasks if it rings again", "Off lets you choose different turn-off tasks for the repeat.", alarm.wakeReuseDismissTasks, !busy) { value ->
    update { it.copy(wakeReuseDismissTasks = value) }
  }
  if (!alarm.wakeReuseDismissTasks) Text("Tasks for the repeat alarm", style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
  if (!alarm.wakeReuseDismissTasks) TaskRows(alarm.wakeRetryTasks, "No tasks — one tap turns the repeat off.", busy, { onEdit("retry|$it") }, { onAdd("retry") })
  TextButton(onClick = onTestWake, enabled = !busy, modifier = Modifier.padding(horizontal = 8.dp)) {
    Ico(R.drawable.ic_play_arrow, size = 20.dp); Spacer(Modifier.width(8.dp)); Text("Try a wake-up check now")
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EscalationSettings(alarm: AlarmDraft, busy: Boolean, update: ((AlarmDraft) -> AlarmDraft) -> Unit,
  onAddSound: (custom: Boolean) -> Unit, onTest: () -> Unit) {
  SwitchRow("Get louder if I don't respond", "Full volume, and a new sound every 45 seconds until you act.", alarm.escalationEnabled, !busy,
    R.drawable.ic_campaign) { value -> update { it.copy(escalationEnabled = value) } }
  if (!alarm.escalationEnabled) return
  HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
  val after = alarm.escalationAfterMinutes
  Stepper("Start after", after, minutesText(after), after > 1, after < 120,
    { update { it.copy(escalationAfterMinutes = nextMinutes(after, false)) } },
    { update { it.copy(escalationAfterMinutes = nextMinutes(after, true).coerceAtMost(120)) } })
  Text("Without choosing Dismiss or Snooze. Starts right away from the third ring after two snoozes.",
    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
  Text("Playlist", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
  val tones = alarm.escalationTones
  fun set(list: List<SoundRef>) = update { it.copy(escalationTones = list) }
  tones.forEachIndexed { index, sound ->
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("${index + 1}.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
      Text(sound.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
      IconButton(onClick = { set(tones.toMutableList().apply { add(index - 1, removeAt(index)) }) }, enabled = !busy && index > 0) {
        Ico(R.drawable.ic_arrow_upward, "Move ${sound.name} up", size = 20.dp)
      }
      IconButton(onClick = { set(tones.toMutableList().apply { add(index + 1, removeAt(index)) }) }, enabled = !busy && index < tones.lastIndex) {
        Ico(R.drawable.ic_arrow_downward, "Move ${sound.name} down", size = 20.dp)
      }
      IconButton(onClick = { set(tones.filterIndexed { i, _ -> i != index }) }, enabled = !busy && tones.size > 1) {
        Ico(R.drawable.ic_close, "Remove ${sound.name}", size = 20.dp)
      }
    }
  }
  Text("Each sound plays for 45 seconds, then the next; the list repeats.", style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
  if (tones.size < 12) FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    SoundRef.BUILTIN.forEach { sound -> AssistChip({ set(tones + sound) }, label = { Text(sound.name) }, enabled = !busy,
      leadingIcon = { Ico(R.drawable.ic_add, size = 18.dp) }) }
    AssistChip({ onAddSound(false) }, label = { Text("Phone sound") }, enabled = !busy, leadingIcon = { Ico(R.drawable.ic_add, size = 18.dp) })
    AssistChip({ onAddSound(true) }, label = { Text("Audio file") }, enabled = !busy, leadingIcon = { Ico(R.drawable.ic_add, size = 18.dp) })
  }
  Row(Modifier.padding(horizontal = 8.dp)) {
    TextButton(onClick = onTest, enabled = !busy) { Ico(R.drawable.ic_play_arrow, size = 20.dp); Spacer(Modifier.width(8.dp)); Text("Test escalation") }
    if (tones != SoundRef.BUILTIN) TextButton(onClick = { set(SoundRef.BUILTIN) }, enabled = !busy) { Text("Reset playlist") }
  }
}

@Composable
private fun EditorActions(busy: Boolean, onTest: () -> Unit, onSave: () -> Unit) {
  Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
    Column(Modifier.navigationBarsPadding()) {
      if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
      Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onTest, enabled = !busy, modifier = Modifier.heightIn(min = 52.dp)) {
          Ico(R.drawable.ic_play_arrow, size = 20.dp); Spacer(Modifier.width(6.dp)); Text("Test")
        }
        Button(onClick = onSave, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
          Text(if (busy) "Saving…" else "Save alarm")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskSheet(task: TaskDraft, onChange: (TaskDraft) -> Unit, onRemove: () -> Unit, onEnroll: () -> Unit, onDone: () -> Unit) {
  var demo by rememberSaveable { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp).navigationBarsPadding(),
    verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      IconBadge(taskIcon(task.kind), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, 48.dp)
      Column {
        Text(taskTitle(task.kind), style = MaterialTheme.typography.titleLarge)
        Text(taskBlurb(task.kind), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    if (task.hasDifficulty) {
      Text("Difficulty", style = MaterialTheme.typography.titleSmall)
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        val levels = listOf("easy", "medium", "hard")
        levels.forEachIndexed { index, level ->
          SegmentedButton(task.difficulty == level, { onChange(task.copy(difficulty = level)) }, SegmentedButtonDefaults.itemShape(index, levels.size)) {
            Text(level.replaceFirstChar(Char::uppercase))
          }
        }
      }
      if (task.kind in listOf("text", "math", "memory")) Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Example", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(task.example, fontFamily = FontFamily.Monospace, fontSize = 22.sp, color = MaterialTheme.colorScheme.secondary)
          Text(when (task.kind) { "text" -> "Case and symbols must match. New text every round."
            "memory" -> "A new sequence every round. Tap \"Show again\" as often as you like."; else -> "New numbers every round." },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      if (task.kind == "breakout") SheetNote("Easy has a wide paddle and slow ball; Hard is narrow and fast.")
      if (task.kind == "maze") SheetNote("Harder mazes have more walls and a faster ball.")
    }
    when (task.kind) {
      "barcode" -> {
        Text("Which barcode?", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
          SegmentedButton(task.barcodeMode == "any", { onChange(task.copy(barcodeMode = "any")) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Any") }
          SegmentedButton(task.barcodeMode == "specific", { onChange(task.copy(barcodeMode = "specific")) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("A specific one") }
        }
        if (task.barcodeMode == "specific") {
          if (task.barcode.isEmpty()) Notice("Scan the item you'll need to get up for — like something in the kitchen.")
          else Notice("Saved: ${task.barcode}", R.drawable.ic_check_circle, MaterialTheme.colorScheme.tertiary)
          FilledTonalButton(onClick = onEnroll, modifier = Modifier.fillMaxWidth()) {
            Ico(R.drawable.ic_qr_code_scanner, size = 20.dp); Spacer(Modifier.width(8.dp))
            Text(if (task.barcode.isEmpty()) "Scan barcode to save" else "Scan a different barcode")
          }
        } else SheetNote("Any readable product barcode or QR code counts.")
      }
      "breakout" -> {
        ChipChoice("Play for", listOf(30, 60, 120, 300), task.gameSeconds, ::secondsText, inset = 0.dp) { onChange(task.copy(gameSeconds = it)) }
        SheetNote("Only time spent moving the paddle counts. 20 seconds without moving resets the timer.")
      }
      "maze" -> {
        Text("Controls", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
          SegmentedButton(task.controlMode == "tilt", { onChange(task.copy(controlMode = "tilt")) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Tilt the phone") }
          SegmentedButton(task.controlMode == "touch", { onChange(task.copy(controlMode = "touch")) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Arrow buttons") }
        }
      }
      "walk" -> {
        ChipChoice("Steps", listOf(10, 20, 30, 50, 100), task.stepTarget, { "$it" }, inset = 0.dp) { onChange(task.copy(stepTarget = it)) }
        SheetNote("Uses the phone's step sensor; Android asks for Physical activity access the first time. Steps count only while the task is on screen.")
      }
      "light" -> {
        ChipChoice("Brightness", listOf(100, 200, 400, 800), task.lightLux, { "$it lux" }, inset = 0.dp) { onChange(task.copy(lightLux = it)) }
        ChipChoice("Hold for", listOf(3, 5, 10), task.lightHoldSeconds, { "$it s" }, inset = 0.dp) { onChange(task.copy(lightHoldSeconds = it)) }
        SheetNote("A lit room is about 100–300 lux; a window in daylight is over 1,000. Your alarm flashlight pauses during this task.")
      }
    }
    if (task.usesSensor) ChipChoice("If the sensor isn't available", listOf("math", "text"), task.fallbackKind,
      { if (it == "math") "Solve math" else "Type text" }, inset = 0.dp) { onChange(task.copy(fallbackKind = it)) }
    Stepper("Rounds", task.rounds, task.rounds.toString(), task.rounds > 1, task.rounds < 10,
      { onChange(task.copy(rounds = task.rounds - 1)) }, { onChange(task.copy(rounds = task.rounds + 1)) }, horizontalPadding = 0.dp)
    if (task.kind in listOf("memory", "breakout", "maze", "walk", "light")) OutlinedButton(onClick = { demo = true }, modifier = Modifier.fillMaxWidth()) {
      Ico(R.drawable.ic_play_arrow, size = 20.dp); Spacer(Modifier.width(8.dp)); Text("Try it")
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onRemove, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
        Ico(R.drawable.ic_delete, size = 20.dp); Spacer(Modifier.width(6.dp)); Text("Remove")
      }
      Spacer(Modifier.weight(1f))
      Button(onClick = onDone) { Text("Done") }
    }
  }
  if (demo) TaskDemoDialog(task) { demo = false }
}

@Composable
private fun SheetNote(text: String) {
  Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Runs a task's real board without touching any alarm, so games and sensors can be tried out. */
@Composable
internal fun TaskDemoDialog(task: TaskDraft, onClose: () -> Unit) {
  var key by remember { mutableStateOf(java.util.UUID.randomUUID().toString()) }
  var finished by remember(key) { mutableStateOf(false) }
  var note by remember(key) { mutableStateOf<String?>(null) }
  val sequence = remember(key) { com.taskalarm.engine.Tasks.generate("memory", task.difficulty).answer }
  androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
      Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Try: ${taskTitle(task.kind)}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
          IconButton(onClick = onClose) { Ico(R.drawable.ic_close, "Close") }
        }
        if (finished) {
          Notice("Done — that's what finishing this task looks like.", R.drawable.ic_check_circle, MaterialTheme.colorScheme.tertiary)
          Button(onClick = { key = java.util.UUID.randomUUID().toString() }) { Text("Try again") }
        } else {
          note?.let { Notice(it) }
          val complete = { finished = true }
          when (task.kind) {
            "memory" -> MemoryBoard(sequence, key, true) { answer -> if (answer == sequence) finished = true else note = "Not quite — tap Show again and retry." }
            "breakout" -> BreakoutBoard(task, key, true, complete)
            "maze" -> MazeBoard(task, key, true, complete) { note = it }
            "walk" -> WalkBoard(task, key, true, complete) { note = it }
            "light" -> LightBoard(task, key, true, complete) { note = it }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoutineSheet(routines: List<Routine>, onApply: (Routine) -> Unit, onDelete: (String) -> Unit, onSave: (String) -> Unit) {
  var name by rememberSaveable { mutableStateOf("") }
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp).navigationBarsPadding()) {
    Text("Routines", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
    Text("Applying keeps this alarm's time, days, name and on/off switch.", style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
    Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(name, { name = it.take(80) }, Modifier.weight(1f), label = { Text("Save this alarm as…") }, singleLine = true,
        shape = MaterialTheme.shapes.medium)
      FilledTonalButton(onClick = { onSave(name); name = "" }, enabled = name.isNotBlank()) { Text("Save") }
    }
    @Composable fun Row(routine: Routine) {
      Row(Modifier.fillMaxWidth().clickable { onApply(routine) }.padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconBadge(taskIcon(routine.alarm.dismissTasks.firstOrNull()?.kind ?: "math"), size = 40.dp)
        Column(Modifier.weight(1f)) {
          Text(routine.name, style = MaterialTheme.typography.titleMedium)
          Text(routine.summary(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!routine.starter) IconButton(onClick = { onDelete(routine.id) }) { Ico(R.drawable.ic_delete, "Delete ${routine.name}") }
      }
    }
    if (routines.isNotEmpty()) {
      Text("Saved", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp))
      routines.forEach { Row(it) }
    }
    Text("Starters", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, top = 16.dp))
    StarterRoutines.forEach { Row(it) }
  }
}

@Composable
internal fun AddTaskSheet(onPick: (String) -> Unit) {
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp).navigationBarsPadding()) {
    Text("Add a task", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
    TaskDraft.KINDS.forEach { kind ->
      Row(Modifier.fillMaxWidth().clickable { onPick(kind) }.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconBadge(taskIcon(kind), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, 44.dp)
        Column(Modifier.weight(1f)) {
          Text(taskTitle(kind), style = MaterialTheme.typography.titleMedium)
          Text(taskBlurb(kind), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
internal fun SoundSheet(alarm: AlarmDraft, onPick: (Boolean) -> Unit, onDefault: () -> Unit, onNormalize: (Boolean) -> Unit,
  onBuiltin: (SoundRef) -> Unit = {}) {
  Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).navigationBarsPadding()) {
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("Alarm sound", style = MaterialTheme.typography.titleLarge)
      Text(alarm.soundName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(8.dp))
    Text("Built in", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 24.dp, top = 4.dp))
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SoundRef.BUILTIN.forEach { sound -> FilterChip(alarm.soundUri == sound.uri, { onBuiltin(sound) }, label = { Text(sound.name) },
        leadingIcon = { Ico(R.drawable.ic_music_note, size = 18.dp) }) }
    }
    NavRow("Phone alarm sounds", "Pick from the sounds on your phone", R.drawable.ic_alarm_clock) { onPick(false) }
    NavRow("Import an audio file", "MP3, M4A, WAV, Ogg… copied into the app", R.drawable.ic_music_note) { onPick(true) }
    if (alarm.soundUri.isNotEmpty()) NavRow("Use the phone's default", null, R.drawable.ic_close, trailing = {}, onClick = onDefault)
    if (alarm.soundUri.isNotEmpty() && !alarm.soundUri.startsWith("builtin:")) {
      SwitchRow("Boost quiet sounds", "Raises the file's peak level without clipping. Up to 5 minutes of audio.", alarm.normalize,
        icon = R.drawable.ic_volume_up, onChange = onNormalize)
    }
    Text("Your phone's alarm volume still sets how loud it plays.", style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(hour: Int, minute: Int, is24: Boolean, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
  val state = rememberTimePickerState(hour, minute, is24)
  var typing by rememberSaveable { mutableStateOf(false) }
  BasicAlertDialog(onDismissRequest = onDismiss) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
      Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Alarm time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp))
        if (typing) TimeInput(state) else TimePicker(state)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = { typing = !typing }) {
            Ico(if (typing) R.drawable.ic_timer else R.drawable.ic_keyboard, if (typing) "Use clock dial" else "Type the time")
          }
          Spacer(Modifier.weight(1f))
          TextButton(onClick = onDismiss) { Text("Cancel") }
          TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
        }
      }
    }
  }
}
